package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.core.ToolExecution;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.observability.AgentMetrics;
import com.sprinkleclaw.core.observability.AgentTracer;
import com.sprinkleclaw.core.observability.NoopAgentMetrics;
import com.sprinkleclaw.core.observability.NoopAgentTracer;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 核心 Agent 执行循环。
 *
 * <h3>执行流程</h3>
 * <pre>
 * while (stopReason == TOOL_USE) {
 *     1. 调用 LoopHook.preLlmCall()
 *     2. 发送 ChatRequest → LLM
 *     3. 调用 LoopHook.postLlmCall()
 *     4. 若 stopReason == TOOL_USE:
 *        a. Doom Loop 检测
 *        b. LoopHook.beforeToolExecution() 拦截/修改/跳过
 *        c. ToolExecutor 并发执行工具（含输出截断）
 *     5. 将工具结果追加到对话历史
 *     6. 调用 LoopHook.postToolExecution()
 * }
 * </pre>
 *
 * <h3>线程安全</h3>
 * <p>主循环为单线程执行，仅调用线程修改 context/messages。
 * 工具执行通过 {@link ToolExecutor} 使用虚拟线程并发，但结果在主线程中合并。</p>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final LlmProvider llmProvider;
    private final ToolExecutor toolExecutor;
    private final AgentContext context;
    private final List<LoopHook> hooks;
    private final AgentErrorHandler errorHandler;
    private final AgentMetrics metrics;
    private final AgentTracer tracer;
    private final LoopGuard guard;

    /**
     * 创建 Agent 执行循环。
     *
     * @param llmProvider  LLM 提供者
     * @param toolExecutor 工具执行器
     * @param context      Agent 上下文
     * @param hooks        生命周期钩子列表
     * @param errorHandler 错误处理器（null 时使用默认）
     * @param metrics      指标收集器（null 时使用 NoOp）
     * @param tracer       追踪器（null 时使用 NoOp）
     */
    public AgentLoop(LlmProvider llmProvider,
                     ToolExecutor toolExecutor,
                     AgentContext context,
                     List<LoopHook> hooks,
                     AgentErrorHandler errorHandler,
                     AgentMetrics metrics,
                     AgentTracer tracer) {
        this.llmProvider = llmProvider;
        this.toolExecutor = toolExecutor;
        this.context = context;
        this.hooks = hooks != null ? List.copyOf(hooks) : List.of();
        this.errorHandler = errorHandler != null ? errorHandler : new DefaultAgentErrorHandler();
        this.metrics = metrics != null ? metrics : NoopAgentMetrics.INSTANCE;
        this.tracer = tracer != null ? tracer : NoopAgentTracer.INSTANCE;
        this.guard = new LoopGuard(context.config());
    }

    /**
     * 运行 Agent 循环，直到 LLM 不再请求工具调用。
     *
     * @return Agent 执行结果
     */
    public AgentResult run() {
        Instant start = Instant.now();
        int iteration = 0;
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        List<ToolExecution> allToolExecutions = new ArrayList<>();
        StopReason lastStopReason = StopReason.END_TURN;
        String lastTextOutput = "";

        ToolContext toolContext = new ToolContext(context.config().workingDirectory());
        tracer.onLoopStart(context);

        try {
            while (true) {
                iteration++;
                try {
                    guard.checkIteration(iteration);
                } catch (LoopGuard.LoopExhaustedException e) {
                    log.warn("循环保护触发: {}", e.getMessage());
                    break;
                }
                log.debug("循环迭代 {}", iteration);

                final int iter = iteration;
                hooks.forEach(h -> h.preLlmCall(context, iter));

                ChatRequest request = ChatRequest.builder()
                        .systemPrompt(context.effectiveSystemPrompt())
                        .messages(context.messages())
                        .tools(context.toolDefinitions())
                        .build();

                metrics.recordLlmCall();
                Instant llmStart = Instant.now();
                ChatResponse response;
                try {
                    response = llmProvider.chat(request);
                } catch (Exception e) {
                    metrics.recordLlmError();
                    AgentErrorHandler.ErrorDecision decision = errorHandler.handle(context, e, iteration);
                    switch (decision) {
                        case AgentErrorHandler.ErrorDecision.Abort abort -> {
                            log.error("Agent 终止: {}", abort.reason());
                            tracer.onLoopEnd(context, iteration);
                            return buildResult(lastTextOutput, StopReason.END_TURN, iteration,
                                    totalInputTokens, totalOutputTokens, allToolExecutions, start);
                        }
                        case AgentErrorHandler.ErrorDecision.Retry retry -> {
                            guard.recordError();
                            context.appendUserMessage(retry.injectedMessage());
                            continue;
                        }
                        case AgentErrorHandler.ErrorDecision.Ignore() -> {
                            continue;
                        }
                    }
                }

                Duration llmDuration = Duration.between(llmStart, Instant.now());
                metrics.recordLlmLatency(llmDuration);
                tracer.onLlmResponse(context, response, iteration);

                guard.recordResponse(response);

                if (response.usage() != null) {
                    totalInputTokens += response.usage().inputTokens();
                    totalOutputTokens += response.usage().outputTokens();
                    metrics.recordTokenUsage(response.usage().inputTokens(), response.usage().outputTokens());
                }

                final int currentIteration = iteration;
                hooks.forEach(h -> h.postLlmCall(context, response, currentIteration));

                context.appendAssistantMessage(response);

                lastStopReason = response.stopReason();
                lastTextOutput = response.textContent();

                if (response.stopReason() != StopReason.TOOL_USE || response.toolCalls().isEmpty()) {
                    break;
                }

                // === Pre-Tool: Doom Loop 检测 + Hook 拦截 ===
                List<ToolUseBlock> toolCalls = response.toolCalls();
                List<ToolUseBlock> approvedCalls = new ArrayList<>();
                List<ToolResult> skippedResults = new ArrayList<>();

                for (ToolUseBlock call : toolCalls) {
                    // Doom Loop 检测
                    if (guard.checkToolLoop(call.name(), call.input())) {
                        log.warn("检测到 Doom Loop: 工具 '{}' 被连续重复调用", call.name());
                        skippedResults.add(ToolResult.error(call.name(),
                                "Doom loop detected: tool '" + call.name()
                                        + "' called repeatedly with same arguments. "
                                        + "Try a different approach.").withCallId(call.id()));
                        continue;
                    }

                    // Hook 拦截
                    ToolInterception interception = ToolInterception.CONTINUE;
                    for (LoopHook hook : hooks) {
                        interception = hook.beforeToolExecution(context, call);
                        if (interception instanceof ToolInterception.Skip) break;
                    }

                    switch (interception) {
                        case ToolInterception.Skip s -> skippedResults.add(ToolResult.error(call.name(),
                                "Skipped: " + s.reason()).withCallId(call.id()));
                        case ToolInterception.Modify m ->
                                approvedCalls.add(new ToolUseBlock(call.id(), call.name(), m.modifiedInput()));
                        default -> approvedCalls.add(call);
                    }
                }

                // === 执行工具 ===
                metrics.recordToolCalls(approvedCalls.size());
                ToolExecutor.ExecutionResult execResult =
                        toolExecutor.executeAll(approvedCalls, toolContext);

                allToolExecutions.addAll(execResult.executions());

                // 合并跳过的结果和实际执行的结果
                List<ToolResult> allResults = new ArrayList<>(skippedResults);
                allResults.addAll(execResult.results());
                context.appendToolResults(allResults);

                final int postToolIteration = iteration;
                hooks.forEach(h -> h.postToolExecution(context, postToolIteration));
            }
        } finally {
            final int finalIteration = iteration;
            hooks.forEach(h -> h.onLoopEnd(context, finalIteration));
            tracer.onLoopEnd(context, finalIteration);
        }

        return buildResult(lastTextOutput, lastStopReason, iteration,
                totalInputTokens, totalOutputTokens, allToolExecutions, start);
    }

    /**
     * 构建最终的 Agent 执行结果。
     */
    private AgentResult buildResult(String output, StopReason stopReason, int iterations,
                                    int inputTokens, int outputTokens,
                                    List<ToolExecution> toolExecutions, Instant start) {
        return new AgentResult(
                output, stopReason, context.messages(),
                iterations, new Usage(inputTokens, outputTokens),
                Duration.between(start, Instant.now()), toolExecutions
        );
    }
}
