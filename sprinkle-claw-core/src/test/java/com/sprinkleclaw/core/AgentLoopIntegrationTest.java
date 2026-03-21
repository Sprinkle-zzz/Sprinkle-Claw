package com.sprinkleclaw.core;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.*;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.*;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import com.sprinkleclaw.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Agent Loop 集成测试。
 * <p>使用模拟的 LlmProvider 验证完整的 Agent 执行流程。</p>
 *
 * @author sprinkle
 * @since 2026/3/21
 */
class AgentLoopIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * 单轮工具调用：LLM 请求工具 → 工具执行 → LLM 返回最终结果。
     */
    @Test
    void singleToolCallRoundTrip() {
        AtomicInteger callCount = new AtomicInteger(0);

        LlmProvider provider = request -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return new ChatResponse(
                        List.of(new ContentBlock.ToolUseBlock("c1", "echo", Map.of("msg", "hello"))),
                        StopReason.TOOL_USE,
                        new Usage(100, 50),
                        "mock-model"
                );
            } else {
                return new ChatResponse(
                        List.of(new ContentBlock.TextBlock("Done: hello")),
                        StopReason.END_TURN,
                        new Usage(80, 30),
                        "mock-model"
                );
            }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());

        AgentConfig config = AgentConfig.builder()
                .workingDirectory(tempDir)
                .build();
        AgentContext context = new AgentContext("Test", config, registry.definitions());
        context.appendUserMessage("Say hello");

        ToolExecutor executor = new ToolExecutor(registry, null);
        AgentLoop loop = new AgentLoop(provider, executor, context,
                List.of(), null, null, null);

        AgentResult result = loop.run();

        assertThat(result.output()).contains("Done: hello");
        assertThat(result.totalIterations()).isEqualTo(2);
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().getFirst().toolName()).isEqualTo("echo");
        assertThat(result.totalUsage().totalTokens()).isEqualTo(260);
    }

    /**
     * 多工具并发执行：LLM 一次请求多个工具 → 全部并发执行 → 结果返回。
     */
    @Test
    void concurrentToolExecution() {
        AtomicInteger callCount = new AtomicInteger(0);

        LlmProvider provider = request -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return new ChatResponse(
                        List.of(
                                new ContentBlock.ToolUseBlock("c1", "echo", Map.of("msg", "a")),
                                new ContentBlock.ToolUseBlock("c2", "echo", Map.of("msg", "b")),
                                new ContentBlock.ToolUseBlock("c3", "echo", Map.of("msg", "c"))
                        ),
                        StopReason.TOOL_USE,
                        new Usage(100, 50),
                        "mock"
                );
            } else {
                return new ChatResponse(
                        List.of(new ContentBlock.TextBlock("All done")),
                        StopReason.END_TURN,
                        new Usage(80, 30),
                        "mock"
                );
            }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());

        AgentConfig config = AgentConfig.builder()
                .workingDirectory(tempDir)
                .build();
        AgentContext context = new AgentContext("Test", config, registry.definitions());
        context.appendUserMessage("Do three things");

        ToolExecutor executor = new ToolExecutor(registry, null);
        AgentLoop loop = new AgentLoop(provider, executor, context,
                List.of(), null, null, null);

        AgentResult result = loop.run();

        assertThat(result.toolExecutions()).hasSize(3);
        assertThat(result.output()).contains("All done");
    }

    /**
     * 循环保护：超过最大迭代次数时终止。
     */
    @Test
    void loopGuardPreventsInfiniteLoop() {
        LlmProvider infiniteProvider = request -> new ChatResponse(
                List.of(new ContentBlock.ToolUseBlock("c1", "echo", Map.of("msg", "loop"))),
                StopReason.TOOL_USE,
                new Usage(10, 10),
                "mock"
        );

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());

        AgentConfig config = AgentConfig.builder()
                .maxLoopIterations(3)
                .workingDirectory(tempDir)
                .build();
        AgentContext context = new AgentContext("Test", config, registry.definitions());
        context.appendUserMessage("Loop forever");

        ToolExecutor executor = new ToolExecutor(registry, null);
        AgentLoop loop = new AgentLoop(infiniteProvider, executor, context,
                List.of(), null, null, null);

        AgentResult result = loop.run();
        assertThat(result.totalIterations()).isLessThanOrEqualTo(4);
    }

    /**
     * 错误恢复：LLM 调用失败时 AgentErrorHandler 触发重试。
     */
    @Test
    void errorRecoveryWithRetry() {
        AtomicInteger callCount = new AtomicInteger(0);

        LlmProvider provider = request -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                throw new com.sprinkleclaw.llm.LlmException(
                        com.sprinkleclaw.llm.LlmException.ErrorKind.SERVER_ERROR,
                        "Temporary failure");
            }
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("Recovered")),
                    StopReason.END_TURN,
                    new Usage(100, 50),
                    "mock"
            );
        };

        ToolRegistry registry = new ToolRegistry();
        AgentConfig config = AgentConfig.builder()
                .workingDirectory(tempDir)
                .build();
        AgentContext context = new AgentContext("Test", config, registry.definitions());
        context.appendUserMessage("Test error recovery");

        ToolExecutor executor = new ToolExecutor(registry, null);
        AgentLoop loop = new AgentLoop(provider, executor, context,
                List.of(), new DefaultAgentErrorHandler(), null, null);

        AgentResult result = loop.run();
        assertThat(result.output()).contains("Recovered");
        assertThat(result.totalIterations()).isGreaterThanOrEqualTo(2);
    }

    /**
     * LoopHook 生命周期回调测试。
     */
    @Test
    void hookLifecycleCallbacks() {
        List<String> events = new ArrayList<>();

        LoopHook trackingHook = new LoopHook() {
            @Override
            public void preLlmCall(AgentContext ctx, int iteration) {
                events.add("pre:" + iteration);
            }

            @Override
            public void postLlmCall(AgentContext ctx, ChatResponse response, int iteration) {
                events.add("post:" + iteration);
            }

            @Override
            public void onLoopEnd(AgentContext ctx, int totalIterations) {
                events.add("end:" + totalIterations);
            }
        };

        LlmProvider provider = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("Hello")),
                StopReason.END_TURN,
                new Usage(10, 10),
                "mock"
        );

        ToolRegistry registry = new ToolRegistry();
        AgentConfig config = AgentConfig.builder().workingDirectory(tempDir).build();
        AgentContext context = new AgentContext("Test", config, registry.definitions());
        context.appendUserMessage("Hi");

        ToolExecutor executor = new ToolExecutor(registry, null);
        AgentLoop loop = new AgentLoop(provider, executor, context,
                List.of(trackingHook), null, null, null);

        loop.run();

        assertThat(events).containsExactly("pre:1", "post:1", "end:1");
    }

    // --- 辅助方法 ---

    private AgentTool echoTool() {
        return new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.of("echo", "Echo input",
                        Map.of("type", "object",
                                "properties", Map.of("msg", Map.of("type", "string"))));
            }

            @Override
            public ToolResult execute(Map<String, Object> input, ToolContext context) {
                return ToolResult.success("echo", "Echo: " + input.getOrDefault("msg", ""));
            }
        };
    }
}
