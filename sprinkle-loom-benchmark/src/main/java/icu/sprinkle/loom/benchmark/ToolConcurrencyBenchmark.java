package icu.sprinkle.loom.benchmark;

import icu.sprinkle.loom.core.loop.ToolExecutor;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import icu.sprinkle.loom.tool.ToolRegistry;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工具并发执行基准测试。
 * <p>对比串行与 Virtual Threads 并发执行多个工具时的延迟差异。</p>
 *
 * <p>运行方式: {@code java -jar sprinkle-loom-benchmark.jar ToolConcurrencyBenchmark}</p>
 *
 * @author sprinkle
 * @since 2026/3/21
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ToolConcurrencyBenchmark {

    private ToolExecutor executor;
    private ToolContext context;
    private List<ContentBlock.ToolUseBlock> singleCall;
    private List<ContentBlock.ToolUseBlock> fourCalls;
    private List<ContentBlock.ToolUseBlock> eightCalls;

    @Setup
    public void setup() {
        ToolRegistry registry = new ToolRegistry();
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            registry.register(new SleepTool("tool_" + idx, 50));
        }
        executor = new ToolExecutor(registry, null);
        context = new ToolContext(Path.of("."));

        singleCall = List.of(new ContentBlock.ToolUseBlock("c0", "tool_0", Map.of()));
        fourCalls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fourCalls.add(new ContentBlock.ToolUseBlock("c" + i, "tool_" + i, Map.of()));
        }
        eightCalls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eightCalls.add(new ContentBlock.ToolUseBlock("c" + i, "tool_" + i, Map.of()));
        }
    }

    @Benchmark
    public Object singleTool() {
        return executor.executeAll(singleCall, context);
    }

    @Benchmark
    public Object fourToolsConcurrent() {
        return executor.executeAll(fourCalls, context);
    }

    @Benchmark
    public Object eightToolsConcurrent() {
        return executor.executeAll(eightCalls, context);
    }

    /**
     * 模拟耗时工具（固定延迟）。
     */
    static class SleepTool implements AgentTool {
        private final String name;
        private final int sleepMs;

        SleepTool(String name, int sleepMs) {
            this.name = name;
            this.sleepMs = sleepMs;
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.of(name, "Sleep tool for benchmark", Map.of("type", "object"));
        }

        @Override
        public ToolResult execute(Map<String, Object> input, ToolContext context) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.success(name, "done");
        }
    }
}
