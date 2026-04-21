package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.core.ToolExecution;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.context.ContextManager;
import com.sprinkleclaw.core.loop.event.AgentEvent;
import com.sprinkleclaw.core.session.SessionManager;
import com.sprinkleclaw.core.observability.AgentMetrics;
import com.sprinkleclaw.core.observability.AgentTracer;
import com.sprinkleclaw.core.observability.NoopAgentMetrics;
import com.sprinkleclaw.core.observability.NoopAgentTracer;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.llm.StreamCallback;
import com.sprinkleclaw.llm.cache.CachePolicy;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final ContextManager contextManager;
    private final SessionManager sessionManager;
    private final CachePolicy cachePolicy;

    /**
     * 创建 Agent 执行循环（完整参数）。
     *
     * @param llmProvider    LLM 提供者
     * @param toolExecutor   工具执行器
     * @param context        Agent 上下文
     * @param hooks          生命周期钩子列表
     * @param errorHandler   错误处理器（null 时使用默认）
     * @param metrics        指标收集器（null 时使用 NoOp）
     * @param tracer         追踪器（null 时使用 NoOp）
     * @param contextManager 上下文压缩调度器（null 时跳过压缩）
     * @param sessionManager 会话管理器（null 时跳过会话持久化）
     */
    public AgentLoop(LlmProvider llmProvider,
                     ToolExecutor toolExecutor,
                     AgentContext context,
                     List<LoopHook> hooks,
                     AgentErrorHandler errorHandler,
                     AgentMetrics metrics,
                     AgentTracer tracer,
                     ContextManager contextManager,
                     SessionManager sessionManager) {
        this(llmProvider, toolExecutor, context, hooks, errorHandler, metrics, tracer,
                contextManager, sessionManager, null);
    }

    /**
     * 创建 Agent 执行循环（完整参数，含 CachePolicy）。
     */
    public AgentLoop(LlmProvider llmProvider,
                     ToolExecutor toolExecutor,
                     AgentContext context,
                     List<LoopHook> hooks,
                     AgentErrorHandler errorHandler,
                     AgentMetrics metrics,
                     AgentTracer tracer,
                     ContextManager contextManager,
                     SessionManager sessionManager,
                     CachePolicy cachePolicy) {
        this.llmProvider = llmProvider;
        this.toolExecutor = toolExecutor;
        this.context = context;
        this.hooks = hooks != null ? List.copyOf(hooks) : List.of();
        this.errorHandler = errorHandler != null ? errorHandler : new DefaultAgentErrorHandler();
        this.metrics = metrics != null ? metrics : NoopAgentMetrics.INSTANCE;
        this.tracer = tracer != null ? tracer : NoopAgentTracer.INSTANCE;
        this.guard = new LoopGuard(context.config());
        this.contextManager = contextManager;
        this.sessionManager = sessionManager;
        this.cachePolicy = cachePolicy != null ? cachePolicy : CachePolicy.NOOP;
    }

    /**
     * 创建 Agent 执行循环（不启用会话管理，兼容第一阶段）。
     */
    public AgentLoop(LlmProvider llmProvider,
                     ToolExecutor toolExecutor,
                     AgentContext context,
                     List<LoopHook> hooks,
                     AgentErrorHandler errorHandler,
                     AgentMetrics metrics,
                     AgentTracer tracer,
                     ContextManager contextManager) {
        this(llmProvider, toolExecutor, context, hooks, errorHandler, metrics, tracer,
                contextManager, null);
    }

    /**
     * 创建 Agent 执行循环（不启用上下文压缩和会话管理，兼容 MVP1）。
     */
    public AgentLoop(LlmProvider llmProvider,
                     ToolExecutor toolExecutor,
                     AgentContext context,
                     List<LoopHook> hooks,
                     AgentErrorHandler errorHandler,
                     AgentMetrics metrics,
                     AgentTracer tracer) {
        this(llmProvider, toolExecutor, context, hooks, errorHandler, metrics, tracer, null, null);
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

        ToolContext toolContext = new ToolContext(
                context.config().workingDirectory(), context.mutableAttributes());
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

                // MVP2: 重置 todo_write 使用标记
                context.setAttribute(TodoReminderHook.ROUND_USED_TODO_WRITE_KEY, false);

                // MVP2: 压缩调度（在 preLlmCall Hook 之前）
                if (contextManager != null) {
                    contextManager.compactIfNeeded(context);
                }

                final int iter = iteration;
                hooks.forEach(h -> h.preLlmCall(context, iter));

                ChatRequest request = ChatRequest.builder()
                        .systemPrompt(context.effectiveSystemPrompt())
                        .messages(context.messages())
                        .tools(context.toolDefinitions())
                        .build();

                if (cachePolicy != CachePolicy.NOOP) {
                    request = cachePolicy.decide(request, llmProvider.capabilities());
                }

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
                    metrics.recordCacheTokens(response.usage().cacheCreationInputTokens(),
                            response.usage().cacheReadInputTokens());
                    if (response.usage().cacheReadInputTokens() > 0) {
                        log.debug("Cache hit: {} tokens read, {} tokens created, hit rate: {}.{}%",
                                response.usage().cacheReadInputTokens(),
                                response.usage().cacheCreationInputTokens(),
                                response.usage().cacheHitRateBp() / 100,
                                response.usage().cacheHitRateBp() % 100);
                    }
                    // MVP2: 将 API usage 写入 AgentContext，供 ContextManager 精确判断溢出
                    context.updateTokenUsage(response.usage());
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
                        if (interception instanceof ToolInterception.Skip) {
                            break;
                        }
                    }

                    switch (interception) {
                        case ToolInterception.Skip s -> skippedResults.add(ToolResult.error(call.name(),
                                "Skipped: " + s.reason()).withCallId(call.id()));
                        case ToolInterception.Modify m ->
                                approvedCalls.add(new ToolUseBlock(call.id(), call.name(), m.modifiedInput()));
                        default -> approvedCalls.add(call);
                    }
                }

                // === 执行工具（支持自适应截断）===
                metrics.recordToolCalls(approvedCalls.size());
                int effectiveMaxBytes = -1;
                if (context.config().toolOutputDynamicTruncation()
                        && context.modelContextWindow() > 0) {
                    effectiveMaxBytes = ToolOutputTruncator.computeMaxOutputBytes(
                            context, context.modelContextWindow());
                }
                ToolExecutor.ExecutionResult execResult =
                        toolExecutor.executeAll(approvedCalls, toolContext, effectiveMaxBytes);

                allToolExecutions.addAll(execResult.executions());

                // 合并跳过的结果和实际执行的结果
                List<ToolResult> allResults = new ArrayList<>(skippedResults);
                allResults.addAll(execResult.results());
                context.appendToolResults(allResults);

                // MVP2: 检查本轮是否执行了 todo_write
                boolean usedTodoWrite = approvedCalls.stream()
                        .anyMatch(c -> "todo_write".equals(c.name()));
                if (usedTodoWrite) {
                    context.setAttribute(TodoReminderHook.ROUND_USED_TODO_WRITE_KEY, true);
                }

                final int postToolIteration = iteration;
                hooks.forEach(h -> h.postToolExecution(context, postToolIteration));

                // MVP2: 自动保存
                if (sessionManager != null) {
                    sessionManager.autoSaveIfNeeded(context, iteration);
                }
            }
        } finally {
            // MVP2: 循环结束最终保存
            if (sessionManager != null) {
                sessionManager.save(context);
            }
            final int finalIteration = iteration;
            hooks.forEach(h -> h.onLoopEnd(context, finalIteration));
            tracer.onLoopEnd(context, finalIteration);
        }

        return buildResult(lastTextOutput, lastStopReason, iteration,
                totalInputTokens, totalOutputTokens, allToolExecutions, start);
    }

    /**
     * 流式运行 Agent 循环，返回 {@link Flow.Publisher} 逐步推送 {@link AgentEvent}。
     * <p>内部启动虚拟线程运行循环，通过 {@link SubmissionPublisher} 发射事件。
     * 使用 {@link LlmProvider#streamChat} 获取流式 token。</p>
     *
     * @return 事件发布者
     * @since 0.5.0 (MVP4)
     */
    public Flow.Publisher<AgentEvent> runStreaming() {
        var publisher = new SubmissionPublisher<AgentEvent>(
                Executors.newVirtualThreadPerTaskExecutor(),
                256,
                (subscriber, event) -> log.warn("丢弃事件（消费者过慢）: {}", event.getClass().getSimpleName())
        );

        Thread.ofVirtual().name("agent-loop-streaming").start(() -> {
            try {
                runStreamingLoop(publisher);
            } catch (Exception e) {
                publisher.submit(AgentEvent.agentError(e, AgentEvent.ErrorPhase.LLM_CALL));
            } finally {
                publisher.close();
            }
        });

        return publisher;
    }

    /**
     * 流式循环内部实现。
     */
    private void runStreamingLoop(SubmissionPublisher<AgentEvent> publisher) {
        Instant start = Instant.now();
        int iteration = 0;
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        List<ToolExecution> allToolExecutions = new ArrayList<>();
        StopReason lastStopReason = StopReason.END_TURN;
        String lastTextOutput = "";
        var tokenIndex = new AtomicInteger(0);

        ToolContext toolContext = new ToolContext(
                context.config().workingDirectory(), context.mutableAttributes());

        try {
            while (true) {
                iteration++;
                try {
                    guard.checkIteration(iteration);
                } catch (LoopGuard.LoopExhaustedException e) {
                    log.warn("循环保护触发: {}", e.getMessage());
                    break;
                }

                context.setAttribute(TodoReminderHook.ROUND_USED_TODO_WRITE_KEY, false);

                if (contextManager != null) {
                    contextManager.compactIfNeeded(context);
                }

                final int iter = iteration;
                hooks.forEach(h -> h.preLlmCall(context, iter));

                ChatRequest request = ChatRequest.builder()
                        .systemPrompt(context.effectiveSystemPrompt())
                        .messages(context.messages())
                        .tools(context.toolDefinitions())
                        .build();

                if (cachePolicy != CachePolicy.NOOP) {
                    request = cachePolicy.decide(request, llmProvider.capabilities());
                }

                publisher.submit(new AgentEvent.LlmCallStart(Instant.now(), iteration));
                Instant llmStart = Instant.now();

                // 流式回调桥接 AgentEvent
                ChatResponse response;
                try {
                    tokenIndex.set(0);
                    response = llmProvider.streamChat(request, new StreamCallback() {
                        @Override
                        public void onToken(String token) {
                            publisher.submit(new AgentEvent.LlmToken(
                                    Instant.now(), token, tokenIndex.getAndIncrement()));
                        }

                        @Override
                        public void onThinkingToken(String token) {
                            publisher.submit(new AgentEvent.ThinkingToken(
                                    Instant.now(), token, tokenIndex.getAndIncrement()));
                        }

                        @Override
                        public void onToolUseInput(String toolUseId, String toolName, String inputChunk) {
                            publisher.submit(new AgentEvent.ToolInputChunk(
                                    Instant.now(), toolUseId, toolName, inputChunk));
                        }
                    });
                } catch (Exception e) {
                    publisher.submit(AgentEvent.agentError(e, AgentEvent.ErrorPhase.LLM_CALL));
                    AgentErrorHandler.ErrorDecision decision = errorHandler.handle(context, e, iter);
                    switch (decision) {
                        case AgentErrorHandler.ErrorDecision.Abort abort -> {
                            return;
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

                guard.recordResponse(response);

                if (response.usage() != null) {
                    totalInputTokens += response.usage().inputTokens();
                    totalOutputTokens += response.usage().outputTokens();
                    metrics.recordCacheTokens(response.usage().cacheCreationInputTokens(),
                            response.usage().cacheReadInputTokens());
                    context.updateTokenUsage(response.usage());

                    publisher.submit(new AgentEvent.LlmCallEnd(Instant.now(), iteration,
                            response.usage().inputTokens(), response.usage().outputTokens()));
                }

                final int currentIteration = iteration;
                hooks.forEach(h -> h.postLlmCall(context, response, currentIteration));

                context.appendAssistantMessage(response);

                lastStopReason = response.stopReason();
                lastTextOutput = response.textContent();

                if (response.stopReason() != StopReason.TOOL_USE || response.toolCalls().isEmpty()) {
                    publisher.submit(new AgentEvent.IterationComplete(
                            Instant.now(), iteration, response.stopReason()));
                    break;
                }

                // 工具执行（与 run() 相同逻辑，增加事件发射）
                List<ToolUseBlock> toolCalls = response.toolCalls();
                List<ToolUseBlock> approvedCalls = new ArrayList<>();
                List<ToolResult> skippedResults = new ArrayList<>();

                for (ToolUseBlock call : toolCalls) {
                    if (guard.checkToolLoop(call.name(), call.input())) {
                        skippedResults.add(ToolResult.error(call.name(),
                                "Doom loop detected: tool '" + call.name()
                                        + "' called repeatedly with same arguments. "
                                        + "Try a different approach.").withCallId(call.id()));
                        continue;
                    }

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

                // 发射工具开始/结束事件
                for (ToolUseBlock call : approvedCalls) {
                    publisher.submit(new AgentEvent.ToolStart(
                            Instant.now(), call.name(), call.id(), call.input()));
                }

                int effectiveMaxBytes = -1;
                if (context.config().toolOutputDynamicTruncation()
                        && context.modelContextWindow() > 0) {
                    effectiveMaxBytes = ToolOutputTruncator.computeMaxOutputBytes(
                            context, context.modelContextWindow());
                }
                Instant toolStart = Instant.now();
                ToolExecutor.ExecutionResult execResult =
                        toolExecutor.executeAll(approvedCalls, toolContext, effectiveMaxBytes);

                allToolExecutions.addAll(execResult.executions());

                for (var exec : execResult.executions()) {
                    publisher.submit(new AgentEvent.ToolEnd(
                            Instant.now(), exec.toolName(), exec.toolCallId(),
                            !exec.isError(), exec.duration()));
                }

                List<ToolResult> allResults = new ArrayList<>(skippedResults);
                allResults.addAll(execResult.results());
                context.appendToolResults(allResults);

                boolean usedTodoWrite = approvedCalls.stream()
                        .anyMatch(c -> "todo_write".equals(c.name()));
                if (usedTodoWrite) {
                    context.setAttribute(TodoReminderHook.ROUND_USED_TODO_WRITE_KEY, true);
                }

                final int postToolIteration = iteration;
                hooks.forEach(h -> h.postToolExecution(context, postToolIteration));

                publisher.submit(new AgentEvent.IterationComplete(
                        Instant.now(), iteration, StopReason.TOOL_USE));

                if (sessionManager != null) {
                    sessionManager.autoSaveIfNeeded(context, iteration);
                }
            }
        } finally {
            if (sessionManager != null) {
                sessionManager.save(context);
            }
            final int finalIteration = iteration;
            hooks.forEach(h -> h.onLoopEnd(context, finalIteration));
        }

        AgentResult result = buildResult(lastTextOutput, lastStopReason, iteration,
                totalInputTokens, totalOutputTokens, allToolExecutions, start);
        publisher.submit(AgentEvent.agentComplete(result));
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
