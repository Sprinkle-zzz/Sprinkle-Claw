package icu.sprinkle.loom.bootstrap;

import icu.sprinkle.loom.core.AgentResult;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoomAsyncApiTest {

    private static LlmProvider mockProvider() {
        return request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("Hello from async!")),
                StopReason.END_TURN,
                new Usage(10, 20),
                "test-model"
        );
    }

    @Test
    void runAsync_returnsCompletableFuture() throws Exception {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .build();

        CompletableFuture<AgentResult> future = claw.runAsync("Hello");
        assertThat(future).isNotNull();

        AgentResult result = future.get();
        assertThat(result.output()).isEqualTo("Hello from async!");
        claw.close();
    }

    @Test
    void chatAsync_returnsCompletableFuture() throws Exception {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .build();

        CompletableFuture<AgentResult> future = claw.chatAsync("Hello chat");
        assertThat(future).isNotNull();

        AgentResult result = future.get();
        assertThat(result.output()).isEqualTo("Hello from async!");
        claw.close();
    }

    @Test
    void resumeAsync_throwsWhenSessionNotEnabled() {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .build();

        var future = claw.resumeAsync("sid", "msg");
        assertThatThrownBy(() -> future.get())
                .hasCauseInstanceOf(IllegalStateException.class);
        claw.close();
    }

    @Test
    void concurrentCall_throwsIllegalStateException() throws Exception {
        var slowProvider = new LlmProvider() {
            @Override
            public ChatResponse chat(icu.sprinkle.loom.protocol.llm.ChatRequest request) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ChatResponse(
                        List.of(new ContentBlock.TextBlock("slow")),
                        StopReason.END_TURN,
                        new Usage(10, 20),
                        "test-model"
                );
            }
        };

        var claw = LoomBuilder.create()
                .llmProvider(slowProvider)
                .build();

        // Start first call and immediately try second
        CompletableFuture<AgentResult> first = claw.runAsync("first");

        // The second call should fail fast because 'first' is already running
        CompletableFuture<AgentResult> second = claw.runAsync("second");
        assertThatThrownBy(() -> second.get())
                .hasCauseInstanceOf(IllegalStateException.class);

        // Wait for first to complete to clean up
        first.get();
        claw.close();
    }

    @Test
    void sequentialCalls_areAllowed() throws Exception {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .build();

        AgentResult r1 = claw.run("first");
        assertThat(r1.output()).isEqualTo("Hello from async!");

        CompletableFuture<AgentResult> r2 = claw.runAsync("second");
        AgentResult result = r2.get();
        assertThat(result.output()).isEqualTo("Hello from async!");
        claw.close();
    }
}
