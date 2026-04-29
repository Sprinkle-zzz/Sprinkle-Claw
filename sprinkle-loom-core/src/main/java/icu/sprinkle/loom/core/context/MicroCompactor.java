package icu.sprinkle.loom.core.context;

import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.ContentBlock.ToolUseBlock;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.protocol.message.Message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Layer 1 微压缩器：将旧的工具结果替换为占位符文本，保留最近 N 个完整的工具结果。
 *
 * <p>核心观察：旧的工具输出（如 3 轮前读取的文件内容）对当前决策的价值极低，
 * 但占据大量 token。将其替换为 {@code [Previous: used {toolName}]} 占位符即可。</p>
 *
 * <h3>替换规则</h3>
 * <ol>
 *   <li>收集所有 {@link ToolResultMessage} 的位置索引</li>
 *   <li>按时间顺序排列，保留最近 {@code keepRecent} 个不替换</li>
 *   <li>对于需要替换的 {@code ToolResultMessage}：内容长度 &gt; {@code minContentLength} 才替换</li>
 *   <li>占位符格式：{@code [Previous: used {toolName}]}</li>
 * </ol>
 *
 * <h3>MVP3 增强</h3>
 * <ul>
 *   <li><b>去重策略</b>：相同工具名+相同参数的连续调用，仅保留最新 tool_result</li>
 *   <li><b>错误裁剪</b>：is_error=true 的 tool_result 在 N 轮后清除对应 tool_use 的 input</li>
 *   <li><b>受保护工具</b>：protectedTools 中的工具输出不被替换/去重</li>
 * </ul>
 *
 * <h3>性能特征</h3>
 * <ul>
 *   <li>时间复杂度：O(n)，n 为消息数量</li>
 *   <li>不调用 LLM</li>
 *   <li>每轮 AgentLoop 迭代前执行</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/24
 */
public final class MicroCompactor {

    private static final Logger log = LoggerFactory.getLogger(MicroCompactor.class);

    /**
     * 错误裁剪的延迟轮次：tool_result is_error=true 在此轮次后清除 input
     */
    private static final int ERROR_PRUNE_AFTER_ROUNDS = 3;

    private final int keepRecent;
    private final int minContentLength;
    private final TokenEstimator tokenEstimator;
    private final Set<String> protectedTools;

    /**
     * 创建微压缩器。
     *
     * @param keepRecent     保留最近 N 个工具结果不替换
     * @param tokenEstimator token 估算器
     */
    public MicroCompactor(int keepRecent, TokenEstimator tokenEstimator) {
        this(keepRecent, tokenEstimator, Set.of());
    }

    /**
     * 创建微压缩器（含受保护工具列表）。
     *
     * @param keepRecent     保留最近 N 个工具结果不替换
     * @param tokenEstimator token 估算器
     * @param protectedTools 受保护的工具名称集合（其输出不会被替换或去重）
     */
    public MicroCompactor(int keepRecent, TokenEstimator tokenEstimator, Set<String> protectedTools) {
        this.keepRecent = Math.max(1, keepRecent);
        this.minContentLength = 100;
        this.tokenEstimator = tokenEstimator;
        this.protectedTools = protectedTools != null ? Set.copyOf(protectedTools) : Set.of();
    }

    /**
     * 执行微压缩，包括旧结果占位替换、去重、错误裁剪。
     * <p>直接修改 context 中的消息列表。</p>
     *
     * @param context Agent 上下文
     * @return 压缩结果（如果未执行任何替换返回 null）
     */
    public CompactionResult compact(AgentContext context) {
        List<Message> messages = context.mutableMessages();
        int tokensBefore = tokenEstimator.estimate(messages);

        int totalReplaced = 0;

        // 1. 去重策略：相同工具+相同参数，仅保留最新
        totalReplaced += deduplicateToolResults(messages);

        // 2. 错误裁剪策略：N 轮后清除失败调用的 input
        totalReplaced += pruneErrorInputs(messages);

        // 3. 旧结果占位替换（原有逻辑）
        totalReplaced += replaceOldToolResults(messages);

        if (totalReplaced == 0) {
            return null;
        }

        int tokensAfter = tokenEstimator.estimate(messages);
        context.invalidateTokenCache();

        log.debug("[MicroCompactor] 处理 {} 个工具结果（去重+错误裁剪+占位替换），节省 ~{} tokens",
                totalReplaced, tokensBefore - tokensAfter);

        return new CompactionResult(
                CompactionResult.CompactionType.MICRO,
                tokensBefore, tokensAfter, totalReplaced, null);
    }

    /**
     * 去重策略：检测相同工具名+相同参数 hash 的 tool_result，仅保留最新一次。
     *
     * @return 被替换的数量
     */
    private int deduplicateToolResults(List<Message> messages) {
        // 收集所有 (toolResultIndex, toolName, inputHash) 三元组
        List<ToolResultRef> refs = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolResultMessage tr) {
                String toolName = resolveToolName(messages, tr.toolCallId());
                if (protectedTools.contains(toolName)) {
                    continue;
                }
                String inputHash = hashToolInput(messages, tr.toolCallId());
                refs.add(new ToolResultRef(i, tr, toolName, inputHash));
            }
        }

        // 从后往前，记录每个 (toolName:inputHash) 的最新位置
        Map<String, Integer> latestPosition = new HashMap<>();
        for (int i = refs.size() - 1; i >= 0; i--) {
            ToolResultRef ref = refs.get(i);
            String key = ref.toolName + ":" + ref.inputHash;
            latestPosition.putIfAbsent(key, i);
        }

        // 替换非最新的重复 tool_result
        int replaced = 0;
        for (int i = 0; i < refs.size(); i++) {
            ToolResultRef ref = refs.get(i);
            String key = ref.toolName + ":" + ref.inputHash;
            if (latestPosition.get(key) != i) {
                // 不是最新的，替换为去重占位符
                messages.set(ref.index, ToolResultMessage.success(
                        ref.message.toolCallId(),
                        "[Duplicate: " + ref.toolName + " — kept latest result]"));
                replaced++;
            }
        }

        if (replaced > 0) {
            log.debug("[MicroCompactor] 去重替换 {} 个重复工具结果", replaced);
        }
        return replaced;
    }

    /**
     * 错误裁剪策略：对 is_error=true 的 tool_result，在距末尾超过 ERROR_PRUNE_AFTER_ROUNDS 轮后，
     * 清除对应 tool_use 的 input 并压缩错误信息。
     *
     * @return 被裁剪的数量
     */
    private int pruneErrorInputs(List<Message> messages) {
        // 计算每个消息距末尾的"轮次"（以 UserMessage 计数）
        int totalUserMessages = 0;
        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                totalUserMessages++;
            }
        }

        int currentRound = 0;
        int pruned = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                currentRound++;
            }

            if (msg instanceof ToolResultMessage tr && tr.isError()) {
                int age = currentRound;
                if (age < ERROR_PRUNE_AFTER_ROUNDS) {
                    continue;
                }

                String toolName = resolveToolName(messages, tr.toolCallId());
                if (protectedTools.contains(toolName)) {
                    continue;
                }

                // 仅处理内容较大的错误结果
                if (tr.content() != null && tr.content().length() > minContentLength) {
                    String errorSummary = extractErrorSummary(tr.content());
                    messages.set(i, ToolResultMessage.error(
                            tr.toolCallId(),
                            "[Error: " + toolName + " failed — " + errorSummary + "]"));

                    // 清除对应 tool_use 的 input（替换为空 input 的 ToolUseBlock）
                    clearToolUseInput(messages, tr.toolCallId());
                    pruned++;
                }
            }
        }

        if (pruned > 0) {
            log.debug("[MicroCompactor] 错误裁剪 {} 个失败工具调用", pruned);
        }
        return pruned;
    }

    /**
     * 原有逻辑：将旧的工具结果替换为占位符。
     *
     * @return 被替换的数量
     */
    private int replaceOldToolResults(List<Message> messages) {
        List<Integer> toolResultIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolResultMessage) {
                toolResultIndices.add(i);
            }
        }

        int replaceCount = toolResultIndices.size() - keepRecent;
        if (replaceCount <= 0) {
            return 0;
        }

        int replaced = 0;
        for (int i = 0; i < replaceCount; i++) {
            int idx = toolResultIndices.get(i);
            ToolResultMessage original = (ToolResultMessage) messages.get(idx);

            if (original.content() != null && original.content().length() <= minContentLength) {
                continue;
            }

            String toolName = resolveToolName(messages, original.toolCallId());

            // 受保护工具不替换
            if (protectedTools.contains(toolName)) {
                continue;
            }

            messages.set(idx, ToolResultMessage.success(
                    original.toolCallId(), "[Previous: used " + toolName + "]"));
            replaced++;
        }

        return replaced;
    }

    /**
     * 对 tool_use 的 input 计算 hash，用于去重判定。
     * <p>对 input JSON 的 key 排序后取 SHA-256 前 16 字符。</p>
     */
    private static String hashToolInput(List<Message> messages, String toolCallId) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage am) {
                for (ContentBlock block : am.content()) {
                    if (block instanceof ToolUseBlock tu && tu.id().equals(toolCallId)) {
                        return computeInputHash(tu.input());
                    }
                }
            }
        }
        return "";
    }

    /**
     * 计算 input Map 的 canonical hash。
     */
    private static String computeInputHash(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "empty";
        }
        // canonical 排序
        TreeMap<String, Object> sorted = new TreeMap<>(input);
        String canonical = sorted.toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available
            return canonical.hashCode() + "";
        }
    }

    /**
     * 清除 AssistantMessage 中指定 toolCallId 的 ToolUseBlock 的 input。
     * <p>由于 ToolUseBlock 是 record（不可变），需要替换整个 AssistantMessage。</p>
     */
    private static void clearToolUseInput(List<Message> messages, String toolCallId) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage am) {
                boolean found = false;
                List<ContentBlock> newBlocks = new ArrayList<>();
                for (ContentBlock block : am.content()) {
                    if (block instanceof ToolUseBlock tu && tu.id().equals(toolCallId)) {
                        newBlocks.add(new ToolUseBlock(tu.id(), tu.name(), Map.of()));
                        found = true;
                    } else {
                        newBlocks.add(block);
                    }
                }
                if (found) {
                    messages.set(i, new AssistantMessage(newBlocks, am.stopReason()));
                    return;
                }
            }
        }
    }

    /**
     * 从错误信息中提取摘要（首行，最多 200 字符）。
     */
    private static String extractErrorSummary(String errorContent) {
        if (errorContent == null || errorContent.isEmpty()) {
            return "unknown error";
        }
        String firstLine = errorContent.split("\n")[0];
        return firstLine.length() > 200
                ? firstLine.substring(0, 200) + "..."
                : firstLine;
    }

    /**
     * 从 AssistantMessage 中解析 ToolResultMessage 关联的工具名称。
     */
    private static String resolveToolName(List<Message> messages, String toolCallId) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage am) {
                for (ContentBlock block : am.content()) {
                    if (block instanceof ToolUseBlock tu && tu.id().equals(toolCallId)) {
                        return tu.name();
                    }
                }
            }
        }
        return "unknown_tool";
    }

    /**
     * 工具结果引用，用于去重判定。
     */
    private record ToolResultRef(int index, ToolResultMessage message,
                                 String toolName, String inputHash) {
    }
}
