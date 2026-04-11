package com.sprinkleclaw.llm.openai;

import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.message.ContentBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponseCollectorTest {

    private OpenAiResponseCollector collector;

    @BeforeEach
    void setUp() {
        collector = new OpenAiResponseCollector();
    }

    @Test
    void buildTextResponse() {
        collector.setModelId("gpt-4o");
        collector.appendContent("Hello ");
        collector.appendContent("World");
        collector.setFinishReason("stop");
        collector.setUsage(100, 50, 0);

        var response = collector.build();
        assertThat(response.content()).hasSize(1);
        assertThat(((ContentBlock.TextBlock) response.content().getFirst()).text()).isEqualTo("Hello World");
        assertThat(response.stopReason()).isEqualTo(StopReason.END_TURN);
        assertThat(response.usage().inputTokens()).isEqualTo(100);
        assertThat(response.usage().outputTokens()).isEqualTo(50);
        assertThat(response.modelId()).isEqualTo("gpt-4o");
    }

    @Test
    void buildToolCallsResponse() {
        collector.appendToolCall(0, "call_001", "read_file", "{\"path\":");
        collector.appendToolCall(0, null, null, "\"test.txt\"}");
        collector.setFinishReason("tool_calls");

        var response = collector.build();
        assertThat(response.content()).hasSize(1);
        var block = (ContentBlock.ToolUseBlock) response.content().getFirst();
        assertThat(block.id()).isEqualTo("call_001");
        assertThat(block.name()).isEqualTo("read_file");
        assertThat(block.input()).containsEntry("path", "test.txt");
        assertThat(response.stopReason()).isEqualTo(StopReason.TOOL_USE);
    }

    @Test
    void parallelToolCalls() {
        collector.appendToolCall(0, "call_001", "read_file", "{\"path\":\"a.txt\"}");
        collector.appendToolCall(1, "call_002", "read_file", "{\"path\":\"b.txt\"}");

        var response = collector.build();
        assertThat(response.content()).hasSize(2);
        var first = (ContentBlock.ToolUseBlock) response.content().get(0);
        var second = (ContentBlock.ToolUseBlock) response.content().get(1);
        assertThat(first.id()).isEqualTo("call_001");
        assertThat(second.id()).isEqualTo("call_002");
    }

    @Test
    void textAndToolCallsCombined() {
        collector.appendContent("Let me check that.");
        collector.appendToolCall(0, "call_001", "bash", "{\"cmd\":\"ls\"}");

        var response = collector.build();
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(response.content().get(1)).isInstanceOf(ContentBlock.ToolUseBlock.class);
    }

    @Test
    void getToolCallIdAndName() {
        collector.appendToolCall(0, "call_x", "bash", "");
        assertThat(collector.getToolCallId(0)).isEqualTo("call_x");
        assertThat(collector.getToolCallName(0)).isEqualTo("bash");
        assertThat(collector.getToolCallId(1)).isNull();
    }

    @Test
    void finishReasonMapping() {
        collector.setFinishReason("length");
        assertThat(collector.build().stopReason()).isEqualTo(StopReason.MAX_TOKENS);
    }

    @Test
    void nullFinishReasonDefaultsToEndTurn() {
        assertThat(collector.build().stopReason()).isEqualTo(StopReason.END_TURN);
    }

    @Test
    void appendNullContentIsIgnored() {
        collector.appendContent(null);
        collector.appendContent("valid");
        var response = collector.build();
        assertThat(((ContentBlock.TextBlock) response.content().getFirst()).text()).isEqualTo("valid");
    }

    @Test
    void usageWithReasoningTokens() {
        collector.setUsage(200, 100, 30);
        var response = collector.build();
        assertThat(response.usage().inputTokens()).isEqualTo(200);
        assertThat(response.usage().outputTokens()).isEqualTo(100);
    }
}
