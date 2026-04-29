package com.sprinkleclaw.protocol;

import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock.TextBlock;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class ChatResponseTest {

    @Test
    void textContent_concatenatesAllTextBlocks() {
        var response = new ChatResponse(
                List.of(new TextBlock("Hello "), new TextBlock("world")),
                StopReason.END_TURN, new Usage(10, 20), "claude-opus-4-7"
        );
        assertThat(response.textContent()).isEqualTo("Hello world");
    }

    @Test
    void toolCalls_extractsOnlyToolUseBlocks() {
        var response = new ChatResponse(
                List.of(
                        new TextBlock("thinking..."),
                        new ToolUseBlock("t1", "bash", Map.of("command", "ls"))
                ),
                StopReason.TOOL_USE, new Usage(10, 20), "claude-opus-4-7"
        );
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().getFirst().name()).isEqualTo("bash");
    }

    @Test
    void usage_totalTokens() {
        var usage = new Usage(100, 50);
        assertThat(usage.totalTokens()).isEqualTo(150);
    }

    @Test
    void empty_response() {
        var response = ChatResponse.empty(StopReason.END_TURN);
        assertThat(response.content()).isEmpty();
        assertThat(response.textContent()).isEmpty();
        assertThat(response.toolCalls()).isEmpty();
    }
}
