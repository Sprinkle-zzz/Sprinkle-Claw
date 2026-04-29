package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.ToolExecution;
import icu.sprinkle.loom.protocol.message.ContentBlock.ToolUseBlock;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import icu.sprinkle.loom.tool.ToolPolicy;
import icu.sprinkle.loom.tool.ToolRegistry;
import icu.sprinkle.loom.tool.error.DefaultToolErrorHandler;
import icu.sprinkle.loom.tool.error.ToolErrorHandler;
import icu.sprinkle.loom.tool.error.ToolErrorHandler.ErrorRecovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 基于工具行为标记的分级并发执行器。
 * <p>将一批工具调用根据 {@link AgentTool#isConcurrencySafe()} 分为并发安全组和独占组：
 * <ul>
 *   <li>并发安全组：通过虚拟线程并行执行</li>
 *   <li>独占组：按顺序串行执行</li>
 * </ul>
 * <p>当所有工具的 {@code isConcurrencySafe()} 都返回默认值 {@code false} 时，
 * 退化为全部串行执行。</p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public final class ConcurrencyAwareToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyAwareToolExecutor.class);

    private final ToolRegistry registry;
    private final ToolPolicy policy;
    private final ToolErrorHandler errorHandler;
    private final ToolOutputTruncator truncator;
    private final ExecutorService executor;

    public ConcurrencyAwareToolExecutor(ToolRegistry registry, ToolPolicy policy,
                                        ToolErrorHandler errorHandler, ToolOutputTruncator truncator) {
        this.registry = registry;
        this.policy = policy;
        this.errorHandler = errorHandler != null ? errorHandler : new DefaultToolErrorHandler();
        this.truncator = truncator;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public ConcurrencyAwareToolExecutor(ToolRegistry registry, ToolPolicy policy) {
        this(registry, policy, null, null);
    }

    /**
     * 工具执行批量结果。
     */
    public record ExecutionResult(List<ToolResult> results, List<ToolExecution> executions) {
    }

    /**
     * 分级并发执行所有工具调用。
     * <p>并发安全组先并行执行，然后独占组串行执行。结果按原始输入顺序返回。</p>
     */
    public ExecutionResult executeAll(List<ToolUseBlock> toolCalls, ToolContext context) {
        return executeAll(toolCalls, context, -1);
    }

    /**
     * 分级并发执行所有工具调用，支持自适应截断字节上限。
     */
    public ExecutionResult executeAll(List<ToolUseBlock> toolCalls, ToolContext context, int effectiveMaxBytes) {
        if (toolCalls.size() == 1) {
            var sr = executeOne(toolCalls.getFirst(), context, effectiveMaxBytes);
            return new ExecutionResult(List.of(sr.result()), List.of(sr.execution()));
        }

        // 按 isConcurrencySafe 分组，保留原始索引
        var concurrent = new ArrayList<IndexedCall>();
        var exclusive = new ArrayList<IndexedCall>();

        for (int i = 0; i < toolCalls.size(); i++) {
            var call = toolCalls.get(i);
            AgentTool tool = registry.get(call.name()).orElse(null);
            if (tool != null && tool.isConcurrencySafe()) {
                concurrent.add(new IndexedCall(i, call));
            } else {
                exclusive.add(new IndexedCall(i, call));
            }
        }

        var results = new SingleResult[toolCalls.size()];

        // 1. 并发安全组：虚拟线程并行
        if (!concurrent.isEmpty()) {
            List<Future<IndexedResult>> futures = new ArrayList<>(concurrent.size());
            for (var ic : concurrent) {
                futures.add(executor.submit(() -> new IndexedResult(
                        ic.index(), executeOne(ic.call(), context, effectiveMaxBytes))));
            }
            for (var f : futures) {
                try {
                    var ir = f.get();
                    results[ir.index()] = ir.result();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Tool execution interrupted", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("Tool execution failed", e.getCause());
                }
            }
        }

        // 2. 独占组：串行执行
        for (var ic : exclusive) {
            results[ic.index()] = executeOne(ic.call(), context, effectiveMaxBytes);
        }

        var toolResults = new ArrayList<ToolResult>(toolCalls.size());
        var executions = new ArrayList<ToolExecution>(toolCalls.size());
        for (var sr : results) {
            toolResults.add(sr.result());
            executions.add(sr.execution());
        }
        return new ExecutionResult(toolResults, executions);
    }

    private record IndexedCall(int index, ToolUseBlock call) {}
    private record IndexedResult(int index, SingleResult result) {}
    private record SingleResult(ToolResult result, ToolExecution execution) {}

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
