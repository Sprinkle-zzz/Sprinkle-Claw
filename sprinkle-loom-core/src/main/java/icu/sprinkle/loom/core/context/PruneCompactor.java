package icu.sprinkle.loom.core.context;

import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.ContentBlock.ToolUseBlock;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.protocol.message.Message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Layer 1.5 裁剪压缩器：清除保护区外的旧工具输出，不调用 LLM。
 *
 * <p>采用比 {@link MicroCompactor} 更激进的策略：<b>完全清除</b>旧的工具输出内容，
 * 但保护最近一段对话的完整性。</p>
 *
 * <h3>动态阈值（v1.1）</h3>
 * <p>保护区和最小裁剪量按模型上下文窗口的比例动态计算，而非固定值。
 * 对长上下文模型（如 GPT-4.1 1M、Gemini 2.5 Pro 1M）避免过早裁剪。</p>
 *
 * <h3>受保护工具（MVP3 新增）</h3>
 * <p>protectedTools 中的工具输出不会被裁剪，确保关键工具（如 load_skill）的输出
 * 在整个对话生命周期内可用。</p>
 *
 * <h3>裁剪算法</h3>
 * <ol>
 *   <li>从 messages 末尾向前遍历</li>
 *   <li>最近 2 轮对话内的所有内容：跳过（绝对保护）</li>
 *   <li>遇到之前的压缩摘要消息（含 "[Conversation compressed]"）：停止</li>
 *   <li>当累计 token &gt; protectTokens 后，将后续遇到的 ToolResultMessage 加入裁剪候选</li>
 *   <li>统计候选裁剪量，若 &lt; minimumPruneTokens：放弃</li>
 *   <li>执行裁剪：将候选 ToolResultMessage 内容替换为 {@code [Pruned: {toolName} output removed]}</li>
 * </ol>
 *
 * <h3>性能特征</h3>
 * <ul>
 *   <li>时间复杂度：O(n)，n 为消息数量</li>
 *   <li>不调用 LLM</li>
 *   <li>仅在 token 接近阈值时触发</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/24
 */
public final class PruneCompactor {

    private static final Logger log = LoggerFactory.getLogger(PruneCompactor.class);

    /**
     * 压缩边界标识：遇到此标记时停止向前裁剪
     */
    private static final String COMPRESSION_MARKER = "[Conversation compressed]";

    private final int protectTokens;
    private final int minimumPruneTokens;
    private final int protectRecentTurns;
    private final TokenEstimator tokenEstimator;
    private final Set<String> protectedTools;

    /**
     * 使用显式阈值创建裁剪压缩器。
     *
     * @param protectTokens      保护区大小（最近 N tokens 的工具输出不裁剪）
     * @param minimumPruneTokens 最小裁剪量（低于此值不执行裁剪）
     * @param tokenEstimator     token 估算器
     */
    public PruneCompactor(int protectTokens, int minimumPruneTokens, TokenEstimator tokenEstimator) {
        this(protectTokens, minimumPruneTokens, tokenEstimator, Set.of());
    }

    /**
     * 使用显式阈值创建裁剪压缩器（含受保护工具列表）。
     *
     * @param protectTokens      保护区大小（最近 N tokens 的工具输出不裁剪）
     * @param minimumPruneTokens 最小裁剪量（低于此值不执行裁剪）
     * @param tokenEstimator     token 估算器
     * @param protectedTools     受保护的工具名称集合（其输出不会被裁剪）
     */
    public PruneCompactor(int protectTokens, int minimumPruneTokens,
                          TokenEstimator tokenEstimator, Set<String> protectedTools) {
        this.protectTokens = protectTokens;
        this.minimumPruneTokens = minimumPruneTokens;
        this.protectRecentTurns = 2;
        this.tokenEstimator = tokenEstimator;
        this.protectedTools = protectedTools != null ? Set.copyOf(protectedTools) : Set.of();
    }

    /**
     * 使用动态阈值创建裁剪压缩器（根据模型上下文窗口自动计算）。
     *
     * @param modelContextWindow 模型上下文窗口大小（token 数）
     * @param tokenEstimator     token 估算器
     */
    public PruneCompactor(int modelContextWindow, TokenEstimator tokenEstimator) {
        this(computeProtectTokens(modelContextWindow),
                computeMinPruneTokens(modelContextWindow),
                tokenEstimator, Set.of());
    }

    /**
     * 使用动态阈值创建裁剪压缩器（含受保护工具列表）。
     *
     * @param modelContextWindow 模型上下文窗口大小（token 数）
     * @param tokenEstimator     token 估算器
     * @param protectedTools     受保护的工具名称集合
     */
    public PruneCompactor(int modelContextWindow, TokenEstimator tokenEstimator, Set<String> protectedTools) {
        this(computeProtectTokens(modelContextWindow),
                computeMinPruneTokens(modelContextWindow),
                tokenEstimator, protectedTools);
    }

    /**
     * 计算动态保护区大小：模型窗口的 20%，下限 40K，上限 200K。
     *
     * @param contextWindow 模型上下文窗口大小
     * @return 保护区 token 数
     */
    public static int computeProtectTokens(int contextWindow) {
        return Math.max(40_000, Math.min(200_000, (int) (contextWindow * 0.20)));
    }

    /**
     * 计算动态最小裁剪量：模型窗口的 10%，下限 20K。
     *
     * @param contextWindow 模型上下文窗口大小
     * @return 最小裁剪量 token 数
     */
    public static int computeMinPruneTokens(int contextWindow) {
        return Math.max(20_000, (int) (contextWindow * 0.10));
    }

    /**
     * 执行裁剪压缩，清除保护区外的旧工具输出。
     *
     * @param context Agent 上下文
     * @return 压缩结果（如果裁剪量不足最小值返回 null）
     */
    public CompactionResult compact(AgentContext context) {
        List<Message> messages = context.mutableMessages();
        int tokensBefore = tokenEstimator.estimate(messages);

        int totalToolTokens = 0;
        int prunedTokens = 0;
        int turns = 0;
        List<PruneCandidate> candidates = new ArrayList<>();

        // 从后往前遍历
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);

            // 统计用户消息轮次
            if (msg instanceof UserMessage) {
                turns++;
            }

            // 最近 2 轮对话绝对保护
            if (turns < protectRecentTurns) {
                continue;
            }

            // 遇到压缩边界停止
            if (isCompressionBoundary(msg)) {
                break;
            }

            // 处理工具结果消息
            if (msg instanceof ToolResultMessage tr) {
                // 受保护工具跳过裁剪
                String toolName = resolveToolName(messages, tr.toolCallId());
                if (protectedTools.contains(toolName)) {
                    continue;
                }

                int estimate = tokenEstimator.estimateText(tr.content());
                totalToolTokens += estimate;

                // 超出保护区后，加入裁剪候选
                if (totalToolTokens > protectTokens) {
                    prunedTokens += estimate;
                    candidates.add(new PruneCandidate(i, tr));
                }
            }
        }

        // 裁剪量不足最小值，放弃
        if (prunedTokens < minimumPruneTokens) {
            return null;
        }

        // 执行裁剪
        for (PruneCandidate candidate : candidates) {
            String toolName = resolveToolName(messages, candidate.message.toolCallId());
            messages.set(candidate.index, ToolResultMessage.success(
                    candidate.message.toolCallId(),
                    "[Pruned: " + toolName + " output removed]"));
        }

        int tokensAfter = tokenEstimator.estimate(messages);
        context.invalidateTokenCache();

        log.info("[PruneCompactor] 裁剪 {} 个工具输出，{} → {} tokens",
                candidates.size(), tokensBefore, tokensAfter);

        return new CompactionResult(
                CompactionResult.CompactionType.PRUNE,
                tokensBefore, tokensAfter, candidates.size(), null);
    }

    /**
     * 判断消息是否为压缩边界（之前的摘要消息）。
     */
    private static boolean isCompressionBoundary(Message msg) {
        if (msg instanceof UserMessage um) {
            for (ContentBlock block : um.content()) {
                if (block instanceof ContentBlock.TextBlock tb
                        && tb.text().contains(COMPRESSION_MARKER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 从 AssistantMessage 中解析工具名称。
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
     * 裁剪候选项。
     */
    private record PruneCandidate(int index, ToolResultMessage message) {
    }
}
