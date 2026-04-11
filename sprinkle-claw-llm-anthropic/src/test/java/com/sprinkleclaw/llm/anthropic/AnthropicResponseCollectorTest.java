package com.sprinkleclaw.llm.anthropic;

import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.message.ContentBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicResponseCollectorTest {

    private AnthropicResponseCollector collector;

    @BeforeEach
    void setUp() {
        collector = new AnthropicResponseCollector();
    }

    @Test
    void buildTextResponse() {
        collector.setMessageId("msg_001");
        collector.setModelId("claude-sonnet-4-20250514");
        collector.setInputTokens(100);
        collector.startContentBlock(0, "text");
        collector.appendText("Hello ");
        collector.appendText("World");
        collector.finalizeContentBlock(0);
        collector.setOutputTokens(50);
        collector.setStopReason("end_turn");

        var response = collector.build();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst()).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(((ContentBlock.TextBlock) response.content().getFirst()).text()).isEqualTo("Hello World");
        assertThat(response.stopReason()).isEqualTo(StopReason.END_TURN);
        assertThat(response.usage().inputTokens()).isEqualTo(100);
        assertThat(response.usage().outputTokens()).isEqualTo(50);
        assertThat(response.modelId()).isEqualTo("claude-sonnet-4-20250514");
    }

    @Test
    void buildToolUseResponse() {
        collector.startToolUse(0, "toolu_001", "read_file");
        collector.appendToolInput(0, "{\"path\":");
        collector.appendToolInput(0, "\"test.txt\"}");
        collector.finalizeContentBlock(0);
        collector.setStopReason("tool_use");

        var response = collector.build();
        assertThat(response.content()).hasSize(1);
        var block = (ContentBlock.ToolUseBlock) response.content().getFirst();
        assertThat(block.id()).isEqualTo("toolu_001");
        assertThat(block.name()).isEqualTo("read_file");
        assertThat(block.input()).containsEntry("path", "test.txt");
        assertThat(response.stopReason()).isEqualTo(StopReason.TOOL_USE);
    }

    @Test
    void buildThinkingResponse() {
        collector.startContentBlock(0, "thinking");
        collector.appendThinking("Let me think...");
        collector.appendThinking(" about this.");
        collector.finalizeContentBlock(0);

        collector.startContentBlock(1, "text");
        collector.appendText("Answer");
        collector.finalizeContentBlock(1);

        var response = collector.build();
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0)).isInstanceOf(ContentBlock.ThinkingBlock.class);
        assertThat(((ContentBlock.ThinkingBlock) response.content().get(0)).thinking())
                .isEqualTo("Let me think... about this.");
        assertThat(response.content().get(1)).isInstanceOf(ContentBlock.TextBlock.class);
    }

    @Test
    void currentToolUseIdAndName() {
        collector.startToolUse(0, "toolu_abc", "bash");
        assertThat(collector.currentToolUseId(0)).isEqualTo("toolu_abc");
        assertThat(collector.currentToolName(0)).isEqualTo("bash");
        assertThat(collector.currentToolUseId(1)).isNull();
    }

    @Test
    void unfinalizedBlocksAreFinalizedOnBuild() {
        collector.startContentBlock(0, "text");
        collector.appendText("unfinalized");

        var response = collector.build();
        assertThat(response.content()).hasSize(1);
        assertThat(((ContentBlock.TextBlock) response.content().getFirst()).text()).isEqualTo("unfinalized");
    }

    @Test
    void stopReasonMapping() {
        collector.setStopReason("max_tokens");
        assertThat(collector.build().stopReason()).isEqualTo(StopReason.MAX_TOKENS);
    }

    @Test
    void nullStopReasonDefaultsToEndTurn() {
        assertThat(collector.build().stopReason()).isEqualTo(StopReason.END_TURN);
    }

    @Test
    void invalidToolInputFallsBackToRaw() {
        collector.startToolUse(0, "toolu_001", "test");
        collector.appendToolInput(0, "not-valid-json");
        collector.finalizeContentBlock(0);

        var response = collector.build();
        var block = (ContentBlock.ToolUseBlock) response.content().getFirst();
        assertThat(block.input()).containsEntry("_raw", "not-valid-json");
    }
}
