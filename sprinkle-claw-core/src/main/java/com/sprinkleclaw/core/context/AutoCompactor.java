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
 * <h3>MVP3 增强：锚定迭代摘要</h3>
 * <ul>
 *   <li><b>首次压缩</b>：全量摘要（现有逻辑不变）</li>
 *   <li><b>后续压缩</b>：增量摘要——将前次摘要与新对话合并，保留早期关键信息</li>
 * </ul>
 *
 * <h3>压缩流程</h3>
 * <ol>
 *   <li>将当前完整对话历史保存到 transcript 文件（可回溯）</li>
 *   <li>检测是否存在前次摘要（判断首条消息是否为压缩标记）</li>
 *   <li>首次：全量摘要；后续：增量摘要（包含前次摘要 + 新对话）</li>
 *   <li>替换消息历史为：[User: "{marker}\n\n{summary}"] + [Assistant: "Understood..."]</li>
 *   <li>回放最后一条用户消息（确保模型知道当前任务）</li>
 * </ol>
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

    private static final String INCREMENTAL_COMPACTION_PROMPT = """
            Below is a previous conversation summary followed by new conversation content.

            PREVIOUS SUMMARY:
            %s

            NEW CONVERSATION:
            %s

            Create an updated summary by merging the previous summary with new information.
            Rules:
            1. Keep all important information from the previous summary
            2. Add new discoveries, completed work, and file changes from the new conversation
            3. Remove information that has been superseded (e.g., a file was edited again)
            4. Follow this template:
            ---
            ## Goal
            [Updated goal(s)]

            ## Instructions
            - [All relevant instructions, both old and new]

            ## Discoveries
            [All notable discoveries, both old and new]

            ## Accomplished
            [Complete list of accomplished work, marking what's new]

            ## Relevant files / directories
            [Updated file list with current state]
            ---""";

    private static final String FALLBACK_SUMMARY = "[Conversation compressed - summary generation failed]";
    private static final String COMPRESSION_MARKER = "[Conversation compressed";
    private static final String ACK_TEXT = "Understood. Continuing with compressed context.";

    /**
     * AgentContext attributes 中的压缩次数计数器键。
     */
    private static final String COMPACTION_COUNT_KEY = "__auto_compaction_count";

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
     * <p>首次压缩使用全量摘要，后续压缩使用增量摘要（锚定迭代）。</p>
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

        // 3. 检测是否存在前次摘要（增量 vs 全量）
        String previousSummary = findPreviousSummary(messages);

        // 4. 更新压缩计数器
        Integer compactionCount = context.getAttribute(COMPACTION_COUNT_KEY);
        int count = (compactionCount != null) ? compactionCount + 1 : 1;
        context.setAttribute(COMPACTION_COUNT_KEY, count);

        // 5. 生成摘要
        String summary;
        try {
            if (previousSummary != null) {
                summary = generateIncrementalSummary(messages, previousSummary);
                log.debug("[AutoCompactor] 使用增量摘要模式（第 {} 次压缩）", count);
            } else {
                summary = generateSummary(messages);
                log.debug("[AutoCompactor] 使用全量摘要模式（首次压缩）");
            }
        } catch (Exception e) {
            log.error("[AutoCompactor] LLM 摘要生成失败，跳过压缩", e);
            return null;
        }

        if (summary == null || summary.isBlank()) {
            summary = FALLBACK_SUMMARY;
        }

        // 6. 替换消息历史
        List<Message> newMessages = new ArrayList<>();
        newMessages.add(UserMessage.of(
                COMPRESSION_MARKER + " (iteration " + count + ")]\n\n" + summary));
        newMessages.add(new AssistantMessage(
                List.of(new ContentBlock.TextBlock(ACK_TEXT)), null));

        // 7. 回放最后一条用户消息
        if (lastUserMsg != null) {
            newMessages.add(lastUserMsg);
        }

        context.replaceMessages(newMessages);
        context.recordCompaction();

        int tokensAfter = tokenEstimator.estimate(context.mutableMessages());

        log.info("[AutoCompactor] LLM 摘要压缩完成（第 {} 次），{} → {} tokens，摘要 {} tokens",
                count, tokensBefore, tokensAfter, tokenEstimator.estimateText(summary));

        return new CompactionResult(
                CompactionResult.CompactionType.AUTO,
                tokensBefore, tokensAfter, messagesCount, summary);
    }

    /**
     * 调用 LLM 生成全量对话摘要。
     */
    private String generateSummary(List<Message> messages) {
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
     * 调用 LLM 生成增量摘要（将前次摘要与新对话合并）。
     */
    private String generateIncrementalSummary(List<Message> messages, String previousSummary) {
        // 提取新对话部分（前次摘要之后的所有消息）
        String newConversation = extractNewConversation(messages);

        String prompt = String.format(INCREMENTAL_COMPACTION_PROMPT,
                previousSummary, newConversation);

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.of(prompt)))
                .maxTokens(summaryMaxTokens)
                .temperature(0.0)
                .build();

        ChatResponse response = llm.chat(request);
        return response.textContent();
    }

    /**
     * 检测消息列表中是否存在前次压缩摘要。
     *
     * @return 前次摘要内容，不存在时返回 null
     */
    private String findPreviousSummary(List<Message> messages) {
        if (messages.isEmpty()) return null;
        Message first = messages.getFirst();
        if (first instanceof UserMessage um) {
            String text = extractTextFromBlocks(um.content());
            if (text.startsWith(COMPRESSION_MARKER)) {
                // 提取摘要内容（跳过 header 行）
                int headerEnd = text.indexOf("]\n\n");
                return headerEnd >= 0 ? text.substring(headerEnd + 3) : null;
            }
        }
        return null;
    }

    /**
     * 提取前次摘要之后的新对话内容。
     */
    private String extractNewConversation(List<Message> messages) {
        // 跳过前两条消息（压缩摘要 + ACK），剩余都是新对话
        int startIndex = 0;
        if (messages.size() >= 2) {
            Message first = messages.getFirst();
            if (first instanceof UserMessage um) {
                String text = extractTextFromBlocks(um.content());
                if (text.startsWith(COMPRESSION_MARKER)) {
                    startIndex = 2; // 跳过摘要 + ACK
                }
            }
        }

        var sb = new StringBuilder();
        for (int i = startIndex; i < messages.size(); i++) {
            Message msg = messages.get(i);
            Map<String, Object> serialized = serializeMessage(msg);
            sb.append(serialized.get("role")).append(": ").append(serialized.get("content")).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 从内容块列表中提取纯文本。
     */
    private static String extractTextFromBlocks(List<ContentBlock> blocks) {
        var sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof ContentBlock.TextBlock tb) {
                sb.append(tb.text());
            }
        }
        return sb.toString();
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
                case ContentBlock.ImageBlock i -> sb.append("[image: ").append(i.mediaType()).append("]");
                case ContentBlock.DocumentBlock d -> sb.append("[document: ").append(d.name()).append("]");
                case ContentBlock.AudioBlock a -> sb.append("[audio]");
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
