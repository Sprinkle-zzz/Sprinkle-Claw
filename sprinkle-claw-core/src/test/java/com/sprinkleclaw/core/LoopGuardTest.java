package com.sprinkleclaw.core;

import com.sprinkleclaw.core.loop.LoopGuard;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class LoopGuardTest {

    @Test
    void checkIteration_throwsWhenExceeded() {
        var config = AgentConfig.builder().maxLoopIterations(3).build();
        var guard = new LoopGuard(config);

        guard.checkIteration(1);
        guard.checkIteration(2);
        guard.checkIteration(3);

        assertThatThrownBy(() -> guard.checkIteration(4))
                .isInstanceOf(LoopGuard.LoopExhaustedException.class)
                .hasMessageContaining("Max loop iterations");
    }

    @Test
    void recordResponse_detectsRepetition() {
        var config = AgentConfig.builder().maxRepetitions(2).build();
        var guard = new LoopGuard(config);

        var sameResponse = new ChatResponse(
                List.of(new ContentBlock.TextBlock("same text")),
                StopReason.END_TURN, new Usage(0, 0), "test"
        );

        guard.recordResponse(sameResponse);
        guard.recordResponse(sameResponse);

        assertThatThrownBy(() -> guard.recordResponse(sameResponse))
                .isInstanceOf(LoopGuard.LoopExhaustedException.class)
                .hasMessageContaining("consecutive identical");
    }

    @Test
    void recordError_throwsAfterConsecutiveErrors() {
        var config = AgentConfig.builder().maxConsecutiveErrors(2).build();
        var guard = new LoopGuard(config);

        guard.recordError();

        assertThatThrownBy(guard::recordError)
                .isInstanceOf(LoopGuard.LoopExhaustedException.class)
                .hasMessageContaining("consecutive errors");
    }

    @Test
    void checkToolLoop_detectsDoomLoop() {
        var config = AgentConfig.builder().doomLoopThreshold(3).build();
        var guard = new LoopGuard(config);

        Map<String, Object> sameInput = Map.of("key", "value");

        assertThat(guard.checkToolLoop("test_tool", sameInput)).isFalse();
        assertThat(guard.checkToolLoop("test_tool", sameInput)).isFalse();
        assertThat(guard.checkToolLoop("test_tool", sameInput)).isTrue();
    }

    @Test
    void checkToolLoop_resetsOnDifferentTool() {
        var config = AgentConfig.builder().doomLoopThreshold(3).build();
        var guard = new LoopGuard(config);

        Map<String, Object> input = Map.of("key", "value");

        assertThat(guard.checkToolLoop("tool_a", input)).isFalse();
        assertThat(guard.checkToolLoop("tool_a", input)).isFalse();
        assertThat(guard.checkToolLoop("tool_b", input)).isFalse();
        assertThat(guard.checkToolLoop("tool_a", input)).isFalse();
    }
}
