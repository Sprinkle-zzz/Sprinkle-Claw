package com.sprinkleclaw.core;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.AgentLoop;
import com.sprinkleclaw.core.loop.ToolExecutor;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.*;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class AgentLoopTest {

    @Test
    void simpleEndTurn_noToolCalls() {
        LlmProvider mockProvider = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("Hello, I'm done.")),
                StopReason.END_TURN,
                new Usage(10, 20),
                "test-model"
        );

        ToolRegistry registry = new ToolRegistry();
        ToolExecutor toolExecutor = new ToolExecutor(registry, null);

        AgentContext context = new AgentContext("system", AgentConfig.DEFAULT, List.of());
        context.addMessage(Message.UserMessage.of("Hi"));

        AgentLoop loop = new AgentLoop(mockProvider, toolExecutor, context,
                List.of(), null, null, null);

        AgentResult result = loop.run();

        assertThat(result.output()).isEqualTo("Hello, I'm done.");
        assertThat(result.stopReason()).isEqualTo(StopReason.END_TURN);
        assertThat(result.totalIterations()).isEqualTo(1);
        assertThat(result.totalUsage().inputTokens()).isEqualTo(10);
        assertThat(result.toolExecutions()).isEmpty();
    }

    @Test
    void toolCallThenEndTurn() {
        AtomicInteger callCount = new AtomicInteger(0);
        LlmProvider mockProvider = request -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return new ChatResponse(
                        List.of(new ContentBlock.ToolUseBlock("t1", "test_tool", Map.of("key", "value"))),
                        StopReason.TOOL_USE,
                        new Usage(10, 15),
                        "test-model"
                );
            }
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("Done after tool.")),
                    StopReason.END_TURN,
                    new Usage(20, 25),
                    "test-model"
            );
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(new com.sprinkleclaw.tool.AgentTool() {
            @Override
            public com.sprinkleclaw.protocol.tool.ToolDefinition definition() {
                return com.sprinkleclaw.protocol.tool.ToolDefinition.of("test_tool", "test", Map.of());
            }

            @Override
            public com.sprinkleclaw.protocol.tool.ToolResult execute(Map<String, Object> input,
                                                                     com.sprinkleclaw.tool.ToolContext ctx) {
                return com.sprinkleclaw.protocol.tool.ToolResult.success("t1", "tool output");
            }
        });

        ToolExecutor toolExecutor = new ToolExecutor(registry, null);
        AgentContext context = new AgentContext("system", AgentConfig.DEFAULT, registry.definitions());
        context.addMessage(Message.UserMessage.of("Use tool"));

        AgentLoop loop = new AgentLoop(mockProvider, toolExecutor, context,
                List.of(), null, null, null);

        AgentResult result = loop.run();

        assertThat(result.totalIterations()).isEqualTo(2);
        assertThat(result.output()).isEqualTo("Done after tool.");
        assertThat(result.stopReason()).isEqualTo(StopReason.END_TURN);
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().getFirst().toolName()).isEqualTo("test_tool");
    }
}
