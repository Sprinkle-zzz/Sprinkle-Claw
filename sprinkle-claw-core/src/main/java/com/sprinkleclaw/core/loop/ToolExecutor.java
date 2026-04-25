package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.ToolExecution;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import com.sprinkleclaw.tool.ToolPolicy;
import com.sprinkleclaw.tool.ToolRegistry;
import com.sprinkleclaw.tool.error.DefaultToolErrorHandler;
import com.sprinkleclaw.tool.error.ToolErrorHandler;
import com.sprinkleclaw.tool.error.ToolErrorHandler.ErrorRecovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 工具执行器，使用虚拟线程并发执行工具调用。
 *
 * <p>执行流程：安全策略检查 → 工具查找 → 执行 → 输出截断 → 错误处理。</p>
 * <p>单个工具调用直接在当前线程执行；多个工具调用时通过 {@code newVirtualThreadPerTaskExecutor()}
 * 并发执行以提升效率。</p>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;
    private final ToolPolicy policy;
    private final ToolErrorHandler errorHandler;
    private final ToolOutputTruncator truncator;
    private final ExecutorService executor;

    public ToolExecutor(ToolRegistry registry, ToolPolicy policy,
                        ToolErrorHandler errorHandler, ToolOutputTruncator truncator) {
        this.registry = registry;
        this.policy = policy;
        this.errorHandler = errorHandler != null ? errorHandler : new DefaultToolErrorHandler();
        this.truncator = truncator;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public ToolExecutor(ToolRegistry registry, ToolPolicy policy, ToolErrorHandler errorHandler) {
        this(registry, policy, errorHandler, null);
    }

    public ToolExecutor(ToolRegistry registry, ToolPolicy policy) {
        this(registry, policy, null, null);
    }

    /**
     * 工具执行批量结果。
     *
     * @param results    工具结果列表（与输入顺序一致）
     * @param executions 工具执行记录列表
     */
    public record ExecutionResult(List<ToolResult> results, List<ToolExecution> executions) {
    }

    /**
     * 并发执行所有工具调用，返回结果与输入顺序一致。
     *
     * @param toolCalls 工具调用列表
     * @param context   工具执行上下文
     * @return 执行结果
     */
    public ExecutionResult executeAll(List<ToolUseBlock> toolCalls, ToolContext context) {
        return executeAll(toolCalls, context, -1);
    }

    /**
     * 并发执行所有工具调用，支持自适应截断字节上限。
     *
     * @param toolCalls         工具调用列表
     * @param context           工具执行上下文
     * @param effectiveMaxBytes 动态截断字节上限（-1 表示使用默认值）
     * @return 执行结果
     */
    public ExecutionResult executeAll(List<ToolUseBlock> toolCalls, ToolContext context, int effectiveMaxBytes) {
        if (toolCalls.size() == 1) {
            return executeSingle(toolCalls.getFirst(), context, effectiveMaxBytes);
        }

        List<Future<SingleResult>> futures = new ArrayList<>(toolCalls.size());
        for (ToolUseBlock call : toolCalls) {
            futures.add(executor.submit(() -> executeOne(call, context, effectiveMaxBytes)));
        }

        List<ToolResult> results = new ArrayList<>(toolCalls.size());
        List<ToolExecution> executions = new ArrayList<>(toolCalls.size());

        for (Future<SingleResult> f : futures) {
            try {
                SingleResult sr = f.get();
                results.add(sr.result());
                executions.add(sr.execution());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Tool execution interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Tool execution failed", e.getCause());
            }
        }

        return new ExecutionResult(results, executions);
    }

    /**
     * 单个工具调用的快速路径（无需并发调度）。
     */
    private ExecutionResult executeSingle(ToolUseBlock call, ToolContext context, int effectiveMaxBytes) {
        SingleResult sr = executeOne(call, context, effectiveMaxBytes);
        return new ExecutionResult(List.of(sr.result()), List.of(sr.execution()));
    }

    private record SingleResult(ToolResult result, ToolExecution execution) {
    }

    /**
     * 执行单个工具调用：安全策略检查 → 工具查找 → 执行 → 输出截断 → 错误处理。
     *
     * @param effectiveMaxBytes 动态截断字节上限（-1 表示使用默认值）
     */
    private SingleResult executeOne(ToolUseBlock call, ToolContext context, int effectiveMaxBytes) {
        Instant start = Instant.now();

        if (policy != null) {
            ToolPolicy.Decision decision = policy.check(call.name(), call.input(), context);
            if (decision == ToolPolicy.Decision.DENY) {
                ToolResult denied = ToolResult.error(call.name(),
                        "Tool '" + call.name() + "' is denied by policy").withCallId(call.id());
                ToolExecution exec = new ToolExecution(call.id(), call.name(),
                        call.input().toString(), denied.output(), true,
                        Duration.between(start, Instant.now()));
                return new SingleResult(denied, exec);
            }
        }

        AgentTool tool = registry.get(call.name()).orElse(null);
        if (tool == null) {
            ToolResult notFound = ToolResult.error(call.name(),
                    "Unknown tool: " + call.name()).withCallId(call.id());
            ToolExecution exec = new ToolExecution(call.id(), call.name(),
                    call.input().toString(), notFound.output(), true,
                    Duration.between(start, Instant.now()));
            return new SingleResult(notFound, exec);
        }

        List<String> validationErrors = InputSchemaValidator.validate(
                call.input(), tool.definition().inputSchema());
        if (!validationErrors.isEmpty()) {
            String msg = "Invalid parameters: " + String.join("; ", validationErrors);
            log.warn("工具 '{}' 参数校验失败: {}", call.name(), msg);
            ToolResult invalid = ToolResult.error(call.name(), msg).withCallId(call.id());
            ToolExecution exec = new ToolExecution(call.id(), call.name(),
                    call.input().toString(), invalid.output(), true,
                    Duration.between(start, Instant.now()));
            return new SingleResult(invalid, exec);
        }

        try {
            ToolResult result = tool.execute(call.input(), context).withCallId(call.id());

            // 输出截断（支持自适应字节上限）
            if (truncator != null && !result.isError()) {
                String truncatedOutput = effectiveMaxBytes > 0
                        ? truncator.truncateIfNeeded(call.name(), result.output(), effectiveMaxBytes)
                        : truncator.truncateIfNeeded(call.name(), result.output());
                if (!truncatedOutput.equals(result.output())) {
                    result = new ToolResult(result.toolCallId(), result.toolName(),
                            truncatedOutput, result.isError());
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            log.debug("工具 '{}' 执行完成，耗时 {}ms", call.name(), elapsed.toMillis());

            ToolExecution exec = new ToolExecution(
                    call.id(), call.name(), call.input().toString(),
                    result.output(), result.isError(), elapsed);
            return new SingleResult(result, exec);

        } catch (Exception e) {
            log.warn("工具 '{}' 执行异常: {}", call.name(), e.getMessage());
            ErrorRecovery recovery = errorHandler.handle(call.name(), call.input(), e);
            Duration elapsed = Duration.between(start, Instant.now());

            String output = switch (recovery) {
                case ErrorRecovery.UseResult r -> r.result();
                case ErrorRecovery.Retry r -> "Error (retries not yet implemented): " + e.getMessage();
                case ErrorRecovery.Propagate() -> "Error: " + e.getMessage();
            };

            ToolExecution exec = new ToolExecution(call.id(), call.name(),
                    call.input().toString(), output, true, elapsed);
            return new SingleResult(ToolResult.error(call.name(), output).withCallId(call.id()), exec);
        }
    }
}
