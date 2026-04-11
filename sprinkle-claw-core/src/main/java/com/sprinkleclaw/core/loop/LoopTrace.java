package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.ToolExecution;
import com.sprinkleclaw.core.loop.resilience.ErrorType;
import com.sprinkleclaw.protocol.llm.StopReason;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 单轮迭代的结构化执行记录。
 * <p>每轮迭代产生一个不可变的 LoopTrace，可用于调试、指标采集、事件推送和事后审计。</p>
 *
 * @param iteration      迭代轮次
 * @param startedAt      开始时间
 * @param duration       总耗时
 * @param llmDuration    LLM 调用耗时
 * @param inputTokens    本轮输入 token 数
 * @param outputTokens   本轮输出 token 数
 * @param toolExecutions 工具执行记录
 * @param stopReason     停止原因
 * @param error          错误类型（如有）
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public record LoopTrace(
        int iteration,
        Instant startedAt,
        Duration duration,
        Duration llmDuration,
        int inputTokens,
        int outputTokens,
        List<ToolExecution> toolExecutions,
        StopReason stopReason,
        Optional<ErrorType> error
) {

    /**
     * LoopTrace 构建器。
     */
    public static class Builder {
        private final int iteration;
        private final Instant startedAt;
        private Duration llmDuration = Duration.ZERO;
        private int inputTokens;
        private int outputTokens;
        private List<ToolExecution> toolExecutions = List.of();
        private StopReason stopReason;
        private ErrorType error;

        public Builder(int iteration) {
            this.iteration = iteration;
            this.startedAt = Instant.now();
        }

        public Builder llmDuration(Duration d) {
            this.llmDuration = d;
            return this;
        }

        public Builder tokens(int input, int output) {
            this.inputTokens = input;
            this.outputTokens = output;
            return this;
        }

        public Builder toolExecutions(List<ToolExecution> executions) {
            this.toolExecutions = List.copyOf(executions);
            return this;
        }

        public Builder stopReason(StopReason reason) {
            this.stopReason = reason;
            return this;
        }

        public Builder error(ErrorType error) {
            this.error = error;
            return this;
        }

        public LoopTrace build() {
            return new LoopTrace(
                    iteration, startedAt,
                    Duration.between(startedAt, Instant.now()),
                    llmDuration, inputTokens, outputTokens,
                    toolExecutions, stopReason,
                    Optional.ofNullable(error)
            );
        }
    }
}
