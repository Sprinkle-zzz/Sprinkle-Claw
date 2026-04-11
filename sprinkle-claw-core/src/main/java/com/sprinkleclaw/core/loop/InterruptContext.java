package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 中断上下文，记录中断时刻的完整信息。
 * <p>用于诊断和恢复：中断源、时间、当前迭代、待执行工具、最后 LLM 响应等。</p>
 *
 * @param source          中断来源
 * @param timestamp       中断时间
 * @param iteration       中断时的迭代轮次
 * @param pendingTools    尚未执行的工具调用
 * @param lastLlmResponse 最后一次 LLM 响应（可能不存在）
 * @param metadata        附加元数据
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public record InterruptContext(
        InterruptSource source,
        Instant timestamp,
        int iteration,
        List<ToolUseBlock> pendingTools,
        Optional<ChatResponse> lastLlmResponse,
        Map<String, Object> metadata
) {

    /**
     * 中断来源枚举。
     */
    public enum InterruptSource {
        /**
         * 超过最大迭代次数
         */
        MAX_ITERATIONS,
        /**
         * 循环超时
         */
        TIMEOUT,
        /**
         * Doom Loop 检测
         */
        DOOM_LOOP,
        /**
         * 连续错误过多
         */
        CONSECUTIVE_ERRORS,
        /**
         * 用户取消
         */
        USER_CANCELLED,
        /**
         * HITL 审批拒绝
         */
        APPROVAL_DENIED,
        /**
         * Fallback 也失败
         */
        ALL_PROVIDERS_FAILED,
        /**
         * 流式空闲超时
         */
        STREAM_IDLE_TIMEOUT
    }

    /**
     * 快速创建中断上下文。
     */
    public static InterruptContext of(InterruptSource source, int iteration) {
        return new InterruptContext(source, Instant.now(), iteration,
                List.of(), Optional.empty(), Map.of());
    }

    /**
     * 带待执行工具的中断上下文。
     */
    public static InterruptContext withPendingTools(InterruptSource source, int iteration,
                                                    List<ToolUseBlock> pendingTools) {
        return new InterruptContext(source, Instant.now(), iteration,
                List.copyOf(pendingTools), Optional.empty(), Map.of());
    }
}
