package com.sprinkleclaw.core.context;

import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.message.Message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layer 2 自动压缩器：调用 LLM 生成结构化摘要替换历史消息。
 *
 * <p>当 MicroCompactor + PruneCompactor 仍无法将 token 降至阈值以下时，
 * 使用 LLM 生成五段式结构化摘要（Goal / Instructions / Discoveries / Accomplished / Files），
 * 然后用摘要替换整个消息历史。</p>
 *
 * <h3>压缩流程</h3>
 * <ol>
 *   <li>将当前完整对话历史保存到 transcript 文件（可回溯）</li>
 *   <li>构建压缩请求：完整消息 + COMPACTION_PROMPT</li>
 *   <li>调用 LLM 生成结构化摘要（maxTokens = 4000）</li>
 *   <li>替换消息历史为：[User: "[Conversation compressed]\n\n{summary}"] + [Assistant: "Understood..."]</li>
 *   <li>回放最后一条用户消息（确保模型知道当前任务）</li>
 * </ol>
 *
 * <h3>错误处理</h3>
 * <ul>
 *   <li>LLM 调用失败：记录错误日志，跳过压缩（不阻断主循环）</li>
 *   <li>摘要为空：使用 fallback 占位文本</li>
 *   <li>Transcript 写入失败：仅日志警告，不阻断压缩</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/24
 */
public final class AutoCompactor {

    private static final Logger log = LoggerFactory.getLogger(AutoCompactor.class);

    private static final String COMPACTION_PROMPT = """
            Provide a detailed prompt for continuing our conversation above.
            Focus on information that would be helpful for continuing the conversation,
            including what we did, what we're doing, which files we're working on,
            and what we're going to do next.
            
            When constructing the summary, stick to this template:
            ---
            ## Goal
            [What goal(s) is the user trying to accomplish?]
            
            ## Instructions
            - [What important instructions did the user give you that are relevant]
            - [If there is a plan or spec, include information about it]
            
            ## Discoveries
            [What notable things were learned during this conversation]
            
            ## Accomplished
            [What work has been completed, what work is still in progress, and what is left?]
            
            ## Relevant files / directories
            [Structured list of relevant files that have been read, edited, or created]
            ---""";

    private static final String FALLBACK_SUMMARY = "[Conversation compressed - summary generation failed]";
    private static final String COMPRESSION_MARKER = "[Conversation compressed]";
    private static final String ACK_TEXT = "Understood. Continuing with compressed context.";

    private final LlmProvider llm;
    private final TokenEstimator tokenEstimator;
    private final Path transcriptDir;
    private final int summaryMaxTokens;

    /**
     * 创建自动压缩器。
     *
     * @param llm            LLM 提供者（用于生成摘要）
     * @param tokenEstimator token 估算器
     * @param transcriptDir  transcript 文件存储目录
     */
    public AutoCompactor(LlmProvider llm, TokenEstimator tokenEstimator, Path transcriptDir) {
        this.llm = llm;
        this.tokenEstimator = tokenEstimator;
        this.transcriptDir = transcriptDir;
        this.summaryMaxTokens = 4000;
    }

    /**
     * 执行 LLM 摘要压缩。
     *
     * @param context Agent 上下文
     * @return 压缩结果；LLM 调用失败时返回 null（不阻断主循环）
     */
    public CompactionResult compact(AgentContext context) {
        List<Message> messages = context.mutableMessages();
        int tokensBefore = tokenEstimator.estimate(messages);
        int messagesCount = messages.size();

        // 1. 保存 transcript
        saveTranscript(context);

        // 2. 查找最后一条用户消息用于回放
        UserMessage lastUserMsg = findLastUserMessage(messages);

        // 3. 调用 LLM 生成摘要
        String summary;
        try {
            summary = generateSummary(messages);
        } catch (Exception e) {
            log.error("[AutoCompactor] LLM 摘要生成失败，跳过压缩", e);
            return null;
        }

        if (summary == null || summary.isBlank()) {
            summary = FALLBACK_SUMMARY;
        }

        // 4. 替换消息历史
        List<Message> newMessages = new ArrayList<>();
        newMessages.add(UserMessage.of(COMPRESSION_MARKER + "\n\n" + summary));
        newMessages.add(new AssistantMessage(
                List.of(new ContentBlock.TextBlock(ACK_TEXT)), null));

        // 5. 回放最后一条用户消息
        if (lastUserMsg != null) {
            newMessages.add(lastUserMsg);
        }

        context.replaceMessages(newMessages);
        context.recordCompaction();

        int tokensAfter = tokenEstimator.estimate(context.mutableMessages());

        log.info("[AutoCompactor] LLM 摘要压缩完成，{} → {} tokens，摘要 {} tokens",
                tokensBefore, tokensAfter, tokenEstimator.estimateText(summary));

        return new CompactionResult(
                CompactionResult.CompactionType.AUTO,
                tokensBefore, tokensAfter, messagesCount, summary);
    }

    /**
     * 调用 LLM 生成对话摘要。
     */
    private String generateSummary(List<Message> messages) {
        // 将 COMPACTION_PROMPT 作为最后一条用户消息追加
        List<Message> requestMessages = new ArrayList<>(messages);
        requestMessages.add(UserMessage.of(COMPACTION_PROMPT));

        ChatRequest request = ChatRequest.builder()
                .messages(requestMessages)
                .maxTokens(summaryMaxTokens)
                .temperature(0.0)
                .build();

        ChatResponse response = llm.chat(request);
        return response.textContent();
    }

    /**
     * 从消息列表中查找最后一条用户消息用于回放。
     *
     * @return 最后一条 UserMessage，不存在时返回 null
     */
    private static UserMessage findLastUserMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage um) {
                return um;
            }
        }
        return null;
    }

    /**
     * 将完整消息历史序列化并保存到 transcript 文件。
     *
     * <p>文件路径：{transcriptDir}/{sessionId}_{compactionIndex}.json</p>
     * <p>写入失败仅记录警告日志，不阻断压缩流程。</p>
     */
    private void saveTranscript(AgentContext context) {
        try {
            Files.createDirectories(transcriptDir);

            String sessionId = context.sessionId() != null ? context.sessionId() : "anonymous";
            int compactionIndex = context.compactionCount() + 1;
            String filename = sessionId + "_" + String.format("%03d", compactionIndex) + ".json";

            Path file = transcriptDir.resolve(filename);
            String json = serializeTranscript(context, compactionIndex);
            Files.writeString(file, json, StandardCharsets.UTF_8);

            log.debug("[AutoCompactor] Transcript 已保存: {}", file);
        } catch (IOException e) {
            log.warn("[AutoCompactor] Transcript 写入失败", e);
        }
    }

    /**
     * 将 transcript 数据序列化为简单 JSON 格式。
     * <p>MVP2 使用简单字符串拼接避免引入额外 JSON 库依赖，
     * MVP3 可替换为 Jackson/Gson。</p>
     */
    private static String serializeTranscript(AgentContext context, int compactionIndex) {
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"sessionId\": \"").append(escapeJson(
                context.sessionId() != null ? context.sessionId() : "anonymous")).append("\",\n");
        sb.append("  \"compactionIndex\": ").append(compactionIndex).append(",\n");
        sb.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"messageCount\": ").append(context.mutableMessages().size()).append(",\n");
        sb.append("  \"messages\": [\n");

        List<Message> messages = context.mutableMessages();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> msgMap = serializeMessage(messages.get(i));
            sb.append("    {");
            int entryIdx = 0;
            for (var entry : msgMap.entrySet()) {
                if (entryIdx++ > 0) sb.append(", ");
                sb.append("\"").append(entry.getKey()).append("\": ");
                if (entry.getValue() instanceof String s) {
                    sb.append("\"").append(escapeJson(s)).append("\"");
                } else {
                    sb.append(entry.getValue());
                }
            }
            sb.append("}");
            if (i < messages.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 将单条消息序列化为简单 Map 结构。
     */
    private static Map<String, Object> serializeMessage(Message msg) {
        var map = new LinkedHashMap<String, Object>();
        switch (msg) {
            case UserMessage um -> {
                map.put("role", "user");
                map.put("content", extractText(um.content()));
            }
            case AssistantMessage am -> {
                map.put("role", "assistant");
                map.put("content", extractText(am.content()));
            }
            case ToolResultMessage tr -> {
                map.put("role", "tool_result");
                map.put("toolCallId", tr.toolCallId());
                map.put("content", tr.content() != null ? tr.content() : "");
                map.put("isError", tr.isError());
            }
        }
        return map;
    }

    /**
     * 从内容块列表中提取纯文本。
     */
    private static String extractText(List<ContentBlock> blocks) {
        var sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            switch (block) {
                case ContentBlock.TextBlock tb -> sb.append(tb.text());
                case ContentBlock.ToolUseBlock tu -> sb.append("[tool_use: ").append(tu.name()).append("]");
                case ContentBlock.ThinkingBlock tk -> sb.append("[thinking]");
            }
        }
        return sb.toString();
    }

    /**
     * 简单 JSON 字符串转义。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
