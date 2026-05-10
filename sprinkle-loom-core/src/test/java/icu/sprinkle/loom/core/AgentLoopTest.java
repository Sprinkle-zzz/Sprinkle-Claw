package icu.sprinkle.loom.core;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.loop.AgentLoop;
import icu.sprinkle.loom.core.loop.ToolExecutor;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.protocol.llm.*;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
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
        registry.register(new icu.sprinkle.loom.tool.AgentTool() {
            @Override
            public icu.sprinkle.loom.protocol.tool.ToolDefinition definition() {
                return icu.sprinkle.loom.protocol.tool.ToolDefinition.of("test_tool", "test", Map.of());
            }

            @Override
            public icu.sprinkle.loom.protocol.tool.ToolResult execute(Map<String, Object> input,
                                                                     icu.sprinkle.loom.tool.ToolContext ctx) {
                return icu.sprinkle.loom.protocol.tool.ToolResult.success("t1", "tool output");
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

    @Test
    void streamingPublishesToolResultBetweenToolStartAndToolEnd() throws Exception {
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
        registry.register(new icu.sprinkle.loom.tool.AgentTool() {
            @Override
            public icu.sprinkle.loom.protocol.tool.ToolDefinition definition() {
                return icu.sprinkle.loom.protocol.tool.ToolDefinition.of("test_tool", "test", Map.of());
            }

            @Override
            public icu.sprinkle.loom.protocol.tool.ToolResult execute(Map<String, Object> input,
                                                                     icu.sprinkle.loom.tool.ToolContext ctx) {
                return icu.sprinkle.loom.protocol.tool.ToolResult.success("test_tool", "tool output");
            }
        });

        ToolExecutor toolExecutor = new ToolExecutor(registry, null);
        AgentContext context = new AgentContext("system", AgentConfig.DEFAULT, registry.definitions());
        context.addMessage(Message.UserMessage.of("Use tool"));
        AgentLoop loop = new AgentLoop(mockProvider, toolExecutor, context,
                List.of(), null, null, null);

        RecordingSubscriber subscriber = new RecordingSubscriber();
        loop.runStreaming().subscribe(subscriber);
        assertThat(subscriber.awaitSubscribed()).isTrue();
        subscriber.request(Long.MAX_VALUE);

        assertThat(subscriber.awaitComplete()).isTrue();
        List<icu.sprinkle.loom.core.loop.event.AgentEvent> events = subscriber.events();
        int startIndex = indexOf(events, icu.sprinkle.loom.core.loop.event.AgentEvent.ToolStart.class);
        int resultIndex = indexOf(events, icu.sprinkle.loom.core.loop.event.AgentEvent.ToolResult.class);
        int endIndex = indexOf(events, icu.sprinkle.loom.core.loop.event.AgentEvent.ToolEnd.class);
        assertThat(startIndex).isLessThan(resultIndex);
        assertThat(resultIndex).isLessThan(endIndex);

        var toolResult = (icu.sprinkle.loom.core.loop.event.AgentEvent.ToolResult) events.get(resultIndex);
        assertThat(toolResult.toolName()).isEqualTo("test_tool");
        assertThat(toolResult.toolUseId()).isEqualTo("t1");
        assertThat(toolResult.output()).isEqualTo("tool output");
        assertThat(toolResult.success()).isTrue();
        assertThat(toolResult.truncated()).isFalse();
    }

    private static int indexOf(List<icu.sprinkle.loom.core.loop.event.AgentEvent> events,
                               Class<?> eventType) {
        for (int i = 0; i < events.size(); i++) {
            if (eventType.isInstance(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static final class RecordingSubscriber
            implements Flow.Subscriber<icu.sprinkle.loom.core.loop.event.AgentEvent> {
        private final List<icu.sprinkle.loom.core.loop.event.AgentEvent> events = new ArrayList<>();
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private final CountDownLatch terminal = new CountDownLatch(1);
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscribed.countDown();
        }

        @Override
        public synchronized void onNext(icu.sprinkle.loom.core.loop.event.AgentEvent item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        void request(long n) {
            subscription.request(n);
        }

        synchronized List<icu.sprinkle.loom.core.loop.event.AgentEvent> events() {
            return List.copyOf(events);
        }

        boolean awaitComplete() throws InterruptedException {
            return terminal.await(2, TimeUnit.SECONDS);
        }

        boolean awaitSubscribed() throws InterruptedException {
            return subscribed.await(2, TimeUnit.SECONDS);
        }
    }
}
