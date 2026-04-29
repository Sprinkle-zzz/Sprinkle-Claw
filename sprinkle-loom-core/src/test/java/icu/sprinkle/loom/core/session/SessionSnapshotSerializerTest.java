package icu.sprinkle.loom.core.session;

import icu.sprinkle.loom.core.AgentConfig;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSnapshotSerializerTest {

    @Test
    void serializeAndDeserializeSessionSnapshot() {
        var messages = List.<Message>of(
                Message.UserMessage.of("Hello"),
                new Message.AssistantMessage(
                        List.of(new ContentBlock.TextBlock("Hi there!")),
                        StopReason.END_TURN)
        );

        var now = Instant.now();
        var snapshot = new SessionSnapshot(
                SessionId.of("session-1"),
                messages,
                Map.of("key", (Object) "value"),
                "system prompt",
                AgentConfig.DEFAULT,
                now,
                now,
                0
        );

        String json = SessionSnapshotSerializer.serialize(snapshot);
        SessionSnapshot restored = SessionSnapshotSerializer.deserialize(json);

        assertThat(restored.sessionId()).isEqualTo(SessionId.of("session-1"));
        assertThat(restored.messages()).hasSize(2);
        assertThat(restored.messages().get(0)).isInstanceOf(Message.UserMessage.class);
        assertThat(restored.messages().get(1)).isInstanceOf(Message.AssistantMessage.class);
        assertThat(restored.metadata()).containsEntry("key", "value");
    }

    @Test
    void serializeAndDeserializeMessages_withAllContentBlockTypes() {
        var messages = List.<Message>of(
                new Message.UserMessage(List.of(
                        new ContentBlock.TextBlock("Check this image"),
                        ContentBlock.ImageBlock.ofBase64("image/png", "aGVsbG8="),
                        ContentBlock.DocumentBlock.ofBase64("application/pdf", "cGRmZGF0YQ==", "doc.pdf")
                )),
                new Message.AssistantMessage(List.of(
                        new ContentBlock.TextBlock("I see the image and document."),
                        new ContentBlock.ThinkingBlock("Analyzing the inputs..."),
                        new ContentBlock.ToolUseBlock("call_1", "my_tool", Map.of("param", "value"))
                ), StopReason.TOOL_USE),
                new Message.ToolResultMessage("call_1", "tool output", false),
                new Message.UserMessage(List.of(
                        new ContentBlock.TextBlock("one more thing"),
                        ContentBlock.AudioBlock.ofBase64("audio/mp3", "bXAzdGVzdA==")
                ))
        );

        String json = SessionSnapshotSerializer.serializeMessages(messages);
        List<Message> restored = SessionSnapshotSerializer.deserializeMessages(json);

        assertThat(restored).hasSize(4);

        // UserMessage with Text + Image + Document
        var userMsg = restored.get(0);
        assertThat(userMsg).isInstanceOf(Message.UserMessage.class);
        var userBlocks = ((Message.UserMessage) userMsg).content();
        assertThat(userBlocks).hasSize(3);
        assertThat(userBlocks.get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(((ContentBlock.TextBlock) userBlocks.get(0)).text()).isEqualTo("Check this image");
        assertThat(userBlocks.get(1)).isInstanceOf(ContentBlock.ImageBlock.class);
        assertThat(userBlocks.get(2)).isInstanceOf(ContentBlock.DocumentBlock.class);

        // AssistantMessage with Text + Thinking + ToolUse
        var assistantMsg = restored.get(1);
        assertThat(assistantMsg).isInstanceOf(Message.AssistantMessage.class);
        var assistantBlocks = ((Message.AssistantMessage) assistantMsg).content();
        assertThat(assistantBlocks).hasSize(3);
        assertThat(assistantBlocks.get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(assistantBlocks.get(1)).isInstanceOf(ContentBlock.ThinkingBlock.class);
        assertThat(((ContentBlock.ThinkingBlock) assistantBlocks.get(1)).thinking())
                .isEqualTo("Analyzing the inputs...");
        assertThat(assistantBlocks.get(2)).isInstanceOf(ContentBlock.ToolUseBlock.class);
        var toolUse = (ContentBlock.ToolUseBlock) assistantBlocks.get(2);
        assertThat(toolUse.id()).isEqualTo("call_1");
        assertThat(toolUse.name()).isEqualTo("my_tool");
        assertThat(toolUse.input()).containsEntry("param", "value");

        // ToolResultMessage
        var toolResult = restored.get(2);
        assertThat(toolResult).isInstanceOf(Message.ToolResultMessage.class);
        var tr = (Message.ToolResultMessage) toolResult;
        assertThat(tr.toolCallId()).isEqualTo("call_1");
        assertThat(tr.content()).isEqualTo("tool output");

        // UserMessage with AudioBlock
        var userMsg2 = restored.get(3);
        assertThat(userMsg2).isInstanceOf(Message.UserMessage.class);
        var userBlocks2 = ((Message.UserMessage) userMsg2).content();
        assertThat(userBlocks2.get(0)).isInstanceOf(ContentBlock.TextBlock.class);
        assertThat(userBlocks2.get(1)).isInstanceOf(ContentBlock.AudioBlock.class);
    }

    @Test
    void serializeMessages_includesTypeDiscriminator() throws Exception {
        var msg = Message.UserMessage.of("hello");
        var mapper = SessionSnapshotSerializer.objectMapper();
        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\"");
        assertThat(json).contains("\"user\"");
    }

    @Test
    void toolResultMessage_roundTrip() throws Exception {
        var msg = new Message.ToolResultMessage("call_1", "error output", true);
        var mapper = SessionSnapshotSerializer.objectMapper();
        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\"");
        assertThat(json).contains("\"tool_result\"");
    }

    @Test
    void serializeMessages_producesTypeDiscriminator() {
        var messages = List.<Message>of(
                Message.ToolResultMessage.error("call_1", "error output")
        );
        String json = SessionSnapshotSerializer.serializeMessages(messages);
        assertThat(json).contains("\"@type\"");
    }

    @Test
    void serializeAndDeserializeEmptyMessages() {
        String json = SessionSnapshotSerializer.serializeMessages(List.of());
        List<Message> restored = SessionSnapshotSerializer.deserializeMessages(json);
        assertThat(restored).isEmpty();
    }

    @Test
    void objectMapper_returnsWorkingCopy() throws Exception {
        var mapper = SessionSnapshotSerializer.objectMapper();
        var messages = List.<Message>of(Message.UserMessage.of("test"));
        var now = Instant.now();
        var snapshot = new SessionSnapshot(
                SessionId.of("s1"), messages, Map.of(), "sp", AgentConfig.DEFAULT, now, now, 0);

        String json = mapper.writeValueAsString(snapshot);
        SessionSnapshot restored = mapper.readValue(json, SessionSnapshot.class);

        assertThat(restored.sessionId()).isEqualTo(SessionId.of("s1"));
    }

    @Test
    void toolResultError_flagIsPreserved() {
        var messages = List.<Message>of(
                Message.ToolResultMessage.error("call_1", "error output")
        );

        String json = SessionSnapshotSerializer.serializeMessages(messages);
        List<Message> restored = SessionSnapshotSerializer.deserializeMessages(json);

        var tr = (Message.ToolResultMessage) restored.get(0);
        assertThat(tr.isError()).isTrue();
        assertThat(tr.content()).isEqualTo("error output");
    }
}
