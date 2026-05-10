package icu.sprinkle.loom.core.loop.event;

import icu.sprinkle.loom.protocol.llm.StopReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventAdapterTest {

    private SseEventAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SseEventAdapter(100);
    }

    @Test
    void llmTokenEventFormat() {
        var event = new AgentEvent.LlmToken(Instant.now(), "hello", 0);
        String sse = adapter.toSse(event);

        assertThat(sse).startsWith("id: 1\n");
        assertThat(sse).contains("event: llm_token\n");
        assertThat(sse).contains("\"token\":\"hello\"");
        assertThat(sse).endsWith("\n\n");
    }

    @Test
    void thinkingTokenEventFormat() {
        var event = new AgentEvent.ThinkingToken(Instant.now(), "hmm", 0);
        String sse = adapter.toSse(event);
        assertThat(sse).contains("event: thinking_token\n");
        assertThat(sse).contains("\"token\":\"hmm\"");
    }

    @Test
    void toolStartEventFormat() {
        var event = new AgentEvent.ToolStart(Instant.now(), "read_file", "toolu_001", null);
        String sse = adapter.toSse(event);
        assertThat(sse).contains("event: tool_start\n");
        assertThat(sse).contains("\"tool\":\"read_file\"");
        assertThat(sse).contains("\"tool_use_id\":\"toolu_001\"");
    }

    @Test
    void toolResultEventFormat() {
        var event = new AgentEvent.ToolResult(
                Instant.now(), "read_file", "toolu_001", "file content",
                true, Duration.ofMillis(12), false, 12, 12);

        String sse = adapter.toSse(event);

        assertThat(sse).contains("event: tool_result\n");
        assertThat(sse).contains("\"tool\":\"read_file\"");
        assertThat(sse).contains("\"tool_use_id\":\"toolu_001\"");
        assertThat(sse).contains("\"output\":\"file content\"");
        assertThat(sse).contains("\"success\":true");
        assertThat(sse).contains("\"duration_ms\":12");
        assertThat(sse).contains("\"truncated\":false");
        assertThat(sse).contains("\"original_bytes\":12");
        assertThat(sse).contains("\"emitted_bytes\":12");
    }

    @Test
    void eventIdsAreMonotonicallyIncreasing() {
        var event = new AgentEvent.LlmToken(Instant.now(), "a", 0);
        String first = adapter.toSse(event);
        String second = adapter.toSse(event);
        String third = adapter.toSse(event);

        assertThat(first).startsWith("id: 1\n");
        assertThat(second).startsWith("id: 2\n");
        assertThat(third).startsWith("id: 3\n");
    }

    @Test
    void replayFromLastEventId() {
        var event1 = new AgentEvent.LlmToken(Instant.now(), "a", 0);
        var event2 = new AgentEvent.LlmToken(Instant.now(), "b", 0);
        var event3 = new AgentEvent.LlmToken(Instant.now(), "c", 0);

        adapter.toSse(event1); // id=1
        adapter.toSse(event2); // id=2
        adapter.toSse(event3); // id=3

        List<String> replayed = adapter.replayFrom(1).toList();
        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0)).contains("id: 2\n");
        assertThat(replayed.get(1)).contains("id: 3\n");
    }

    @Test
    void replayFromZeroReturnsAll() {
        adapter.toSse(new AgentEvent.LlmToken(Instant.now(), "a", 0));
        adapter.toSse(new AgentEvent.LlmToken(Instant.now(), "b", 0));

        List<String> replayed = adapter.replayFrom(0).toList();
        assertThat(replayed).hasSize(2);
    }

    @Test
    void iterationCompleteEvent() {
        var event = new AgentEvent.IterationComplete(Instant.now(), 3, StopReason.TOOL_USE);
        String sse = adapter.toSse(event);
        assertThat(sse).contains("event: iteration_complete\n");
        assertThat(sse).contains("\"iteration\":3");
        assertThat(sse).contains("\"stop_reason\":\"TOOL_USE\"");
    }

    @Test
    void errorRecoveredEvent() {
        var event = new AgentEvent.ErrorRecovered(Instant.now(), "RATE_LIMITED", "retry", 2);
        String sse = adapter.toSse(event);
        assertThat(sse).contains("event: error_recovered\n");
        assertThat(sse).contains("\"error_type\":\"RATE_LIMITED\"");
        assertThat(sse).contains("\"attempt\":2");
    }

    @Test
    void noLogModeReplayReturnsEmpty() {
        var noLog = new SseEventAdapter(0);
        noLog.toSse(new AgentEvent.LlmToken(Instant.now(), "a", 0));
        assertThat(noLog.replayFrom(0).toList()).isEmpty();
    }

    @Test
    void logTrimsWhenExceedingMaxSize() {
        var small = new SseEventAdapter(3);
        for (int i = 0; i < 5; i++) {
            small.toSse(new AgentEvent.LlmToken(Instant.now(), String.valueOf(i), 0));
        }
        // Only events 3,4,5 remain (ids 3,4,5)
        List<String> all = small.replayFrom(0).toList();
        assertThat(all).hasSize(3);
        assertThat(all.getFirst()).contains("id: 3\n");
    }
}
