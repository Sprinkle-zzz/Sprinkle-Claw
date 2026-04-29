package icu.sprinkle.loom.protocol;

import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class MessageTest {

    @Test
    void userMessage_of_createsTextMessage() {
        Message.UserMessage msg = Message.UserMessage.of("hello");
        assertThat(msg.content()).hasSize(1);
        assertThat(msg.content().getFirst()).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(((ContentBlock.TextBlock) msg.content().getFirst()).text()).isEqualTo("hello");
    }

    @Test
    void toolResultMessage_success() {
        var result = Message.ToolResultMessage.success("id-1", "output");
        assertThat(result.toolCallId()).isEqualTo("id-1");
        assertThat(result.content()).isEqualTo("output");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void toolResultMessage_error() {
        var result = Message.ToolResultMessage.error("id-2", "failed");
        assertThat(result.isError()).isTrue();
    }

    @Test
    void contentBlock_sealedPermitAll() {
        ContentBlock text = new ContentBlock.TextBlock("hello");
        ContentBlock toolUse = new ContentBlock.ToolUseBlock("id", "name", Map.of());
        ContentBlock thinking = new ContentBlock.ThinkingBlock("hmm");

        assertThat(text).isInstanceOf(ContentBlock.class);
        assertThat(toolUse).isInstanceOf(ContentBlock.class);
        assertThat(thinking).isInstanceOf(ContentBlock.class);
    }
}
