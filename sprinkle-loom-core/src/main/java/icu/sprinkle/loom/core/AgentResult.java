package icu.sprinkle.loom.core;

import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 执行结果，包含最终输出、对话历史、token 用量和工具执行记录。
 *
 * @param output              LLM 最终文本输出
 * @param stopReason          最终停止原因
 * @param conversationHistory 完整对话历史
 * @param totalIterations     总循环迭代次数
 * @param totalUsage          累计 token 用量
 * @param elapsed             总耗时
 * @param toolExecutions      所有工具执行记录
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public record AgentResult(
        String output,
        StopReason stopReason,
        List<Message> conversationHistory,
        int totalIterations,
        Usage totalUsage,
        Duration elapsed,
        List<ToolExecution> toolExecutions
) {

    /**
     * 聚合所有轮次的思考内容（DeepSeek/Qwen/Kimi/GLM/豆包 等思考模型的 {@code reasoning_content}）。
     *
     * <p>按需计算，不预存：调用一次为 O(N) 遍历，不调用零开销。
     * 非思考模型或思考被关闭时返回空串。多轮以 {@code "\n\n"} 分隔。</p>
     */
    public String thinking() {
        return conversationHistory.stream()
                .filter(Message.AssistantMessage.class::isInstance)
                .flatMap(m -> ((Message.AssistantMessage) m).content().stream())
                .filter(ContentBlock.ThinkingBlock.class::isInstance)
                .map(b -> ((ContentBlock.ThinkingBlock) b).thinking())
                .collect(Collectors.joining("\n\n"));
    }
}
