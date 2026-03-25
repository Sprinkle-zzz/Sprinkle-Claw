package com.sprinkleclaw.core.context;

import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.message.Message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    private final int keepRecent;
    private final int minContentLength;
    private final TokenEstimator tokenEstimator;

    /**
     * 创建微压缩器。
     *
     * @param keepRecent     保留最近 N 个工具结果不替换
     * @param tokenEstimator token 估算器
     */
    public MicroCompactor(int keepRecent, TokenEstimator tokenEstimator) {
        this.keepRecent = Math.max(1, keepRecent);
        this.minContentLength = 100;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 执行微压缩，替换旧的工具结果为占位符。
     * <p>直接修改 context 中的消息列表。</p>
     *
     * @param context Agent 上下文
     * @return 压缩结果（如果未执行任何替换返回 null）
     */
    public CompactionResult compact(AgentContext context) {
        List<Message> messages = context.mutableMessages();
        int tokensBefore = tokenEstimator.estimate(messages);

        // 收集所有 ToolResultMessage 的索引
        List<Integer> toolResultIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolResultMessage) {
                toolResultIndices.add(i);
            }
        }

        // 保留最近 keepRecent 个不替换
        int replaceCount = toolResultIndices.size() - keepRecent;
        if (replaceCount <= 0) {
            return null;
        }

        int replaced = 0;
        for (int i = 0; i < replaceCount; i++) {
            int idx = toolResultIndices.get(i);
            ToolResultMessage original = (ToolResultMessage) messages.get(idx);

            // 短内容（≤ minContentLength 字符）保留原文，替换收益太小
            if (original.content() != null && original.content().length() <= minContentLength) {
                continue;
            }

            String toolName = resolveToolName(messages, original.toolCallId());
            messages.set(idx, ToolResultMessage.success(
                    original.toolCallId(), "[Previous: used " + toolName + "]"));
            replaced++;
        }

        if (replaced == 0) {
            return null;
        }

        int tokensAfter = tokenEstimator.estimate(messages);
        // 微压缩后使缓存失效
        context.invalidateTokenCache();

        log.debug("[MicroCompactor] 替换 {} 个旧工具结果为占位符，节省 ~{} tokens",
                replaced, tokensBefore - tokensAfter);

        return new CompactionResult(
                CompactionResult.CompactionType.MICRO,
                tokensBefore, tokensAfter, replaced, null);
    }

    /**
     * 从 AssistantMessage 中解析 ToolResultMessage 关联的工具名称。
     *
     * <p>查找路径：ToolResultMessage.toolCallId → 向前搜索 AssistantMessage
     * → 找到包含相同 id 的 ToolUseBlock → 提取 name。</p>
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
}
