package com.sprinkleclaw.spring.actuator;

import com.sprinkleclaw.core.observability.AgentMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * 基于 Micrometer 的 {@link AgentMetrics} 实现。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class MicrometerAgentMetrics implements AgentMetrics {

    private final Counter llmCalls;
    private final Counter llmErrors;
    private final Timer llmLatency;
    private final Counter toolCalls;
    private final Counter tokensInput;
    private final Counter tokensOutput;

    public MicrometerAgentMetrics(MeterRegistry registry) {
        this.llmCalls = Counter.builder("sprinkle_claw.llm.calls")
                .description("LLM 调用次数").register(registry);
        this.llmErrors = Counter.builder("sprinkle_claw.llm.errors")
                .description("LLM 错误次数").register(registry);
        this.llmLatency = Timer.builder("sprinkle_claw.llm.latency")
                .description("LLM 调用延迟").register(registry);
        this.toolCalls = Counter.builder("sprinkle_claw.tools.calls")
                .description("工具调用次数").register(registry);
        this.tokensInput = Counter.builder("sprinkle_claw.tokens.input")
                .description("输入 token 总量").register(registry);
        this.tokensOutput = Counter.builder("sprinkle_claw.tokens.output")
                .description("输出 token 总量").register(registry);
    }

    @Override
    public void recordLlmCall() {
        llmCalls.increment();
    }

    @Override
    public void recordLlmError() {
        llmErrors.increment();
    }

    @Override
    public void recordLlmLatency(Duration duration) {
        llmLatency.record(duration);
    }

    @Override
    public void recordToolCalls(int count) {
        toolCalls.increment(count);
    }

    @Override
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        tokensInput.increment(inputTokens);
        tokensOutput.increment(outputTokens);
    }
}
