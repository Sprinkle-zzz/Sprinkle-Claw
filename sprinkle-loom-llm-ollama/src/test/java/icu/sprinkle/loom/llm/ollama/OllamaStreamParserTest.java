package icu.sprinkle.loom.llm.ollama;

import icu.sprinkle.loom.llm.LlmCapabilities;
import icu.sprinkle.loom.llm.LlmException;
import icu.sprinkle.loom.llm.StreamCallback;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaStreamParserTest {

    private static final LlmCapabilities TOOL_CAPABLE = LlmCapabilities.builder()
            .supportsToolUse(true).supportsStreaming(true)
            .contextWindowTokens(128_000).maxOutputTokens(8192).build();

    private static final LlmCapabilities NO_TOOL = LlmCapabilities.builder()
            .supportsToolUse(false).supportsStreaming(true)
            .contextWindowTokens(8_192).maxOutputTokens(2048).build();

    private ByteArrayInputStream toStream(String... lines) {
        String joined = String.join("\n", lines) + "\n";
        return new ByteArrayInputStream(joined.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void consume_textChunks_callbackReceivesAllTokens() throws Exception {
        List<String> tokens = new ArrayList<>();
        StreamCallback callback = tokens::add;
        var parser = new OllamaStreamParser(callback, TOOL_CAPABLE);

        ChatResponse resp = parser.consume(toStream(
                "{\"message\":{\"content\":\"Hello\"},\"done\":false}",
                "{\"message\":{\"content\":\" world\"},\"done\":false}",
                "{\"message\":{\"content\":\"!\"},\"done\":true,\"model\":\"llama3.1\",\"prompt_eval_count\":10,\"eval_count\":3}"
        ));

        assertThat(tokens).containsExactly("Hello", " world", "!");
        assertThat(resp.textContent()).isEqualTo("Hello world!");
    }

    @Test
    void consume_doneChunk_extractsUsage() throws Exception {
        var parser = new OllamaStreamParser(token -> {}, TOOL_CAPABLE);

        ChatResponse resp = parser.consume(toStream(
                "{\"message\":{\"content\":\"Hi\"},\"done\":true,\"model\":\"test\",\"prompt_eval_count\":42,\"eval_count\":7}"
        ));

        assertThat(resp.usage().inputTokens()).isEqualTo(42);
        assertThat(resp.usage().outputTokens()).isEqualTo(7);
        assertThat(resp.modelId()).isEqualTo("test");
    }

    @Test
    void consume_errorChunk_throwsLlmException() {
        var parser = new OllamaStreamParser(token -> {}, TOOL_CAPABLE);

        assertThatThrownBy(() -> parser.consume(toStream(
                "{\"error\":\"model not found\"}"
        ))).isInstanceOf(LlmException.class)
                .hasMessageContaining("model not found");
    }

    @Test
    void consume_withNativeToolCalls_assemblesToolUseBlocks() throws Exception {
        var parser = new OllamaStreamParser(token -> {}, TOOL_CAPABLE);

        ChatResponse resp = parser.consume(toStream(
                "{\"message\":{\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"bash\",\"arguments\":{\"cmd\":\"ls\"}}}]},\"done\":true,\"model\":\"llama3.1\",\"prompt_eval_count\":5,\"eval_count\":3}"
        ));

        assertThat(resp.toolCalls()).hasSize(1);
        assertThat(resp.toolCalls().getFirst().name()).isEqualTo("bash");
        assertThat(resp.stopReason()).isEqualTo(StopReason.TOOL_USE);
    }

    @Test
    void consume_noToolSupport_textParsedByBridge() throws Exception {
        var parser = new OllamaStreamParser(token -> {}, NO_TOOL);

        String toolJson = "{\"tool\": \"bash\", \"input\": {\"cmd\": \"ls\"}}";
        ChatResponse resp = parser.consume(toStream(
                "{\"message\":{\"content\":\"```json\\n" + toolJson.replace("\"", "\\\"") + "\\n```\"},\"done\":true,\"model\":\"mistral\",\"prompt_eval_count\":5,\"eval_count\":10}"
        ));

        assertThat(resp.toolCalls()).hasSize(1);
        assertThat(resp.toolCalls().getFirst().name()).isEqualTo("bash");
        assertThat(resp.stopReason()).isEqualTo(StopReason.TOOL_USE);
    }

    @Test
    void consume_emptyStream_returnsEmptyResponse() throws Exception {
        var parser = new OllamaStreamParser(token -> {}, TOOL_CAPABLE);

        ChatResponse resp = parser.consume(toStream(
                "{\"message\":{\"content\":\"\"},\"done\":true,\"model\":\"test\",\"prompt_eval_count\":0,\"eval_count\":0}"
        ));

        assertThat(resp.textContent()).isEmpty();
        assertThat(resp.stopReason()).isEqualTo(StopReason.END_TURN);
    }

    @Test
    void consume_stopReason_textMeansEndTurn() throws Exception {
        var parser = new OllamaStreamParser(token -> {}, TOOL_CAPABLE);

        ChatResponse resp = parser.consume(toStream(
                "{\"message\":{\"content\":\"Just text\"},\"done\":true,\"model\":\"test\",\"prompt_eval_count\":1,\"eval_count\":2}"
        ));

        assertThat(resp.stopReason()).isEqualTo(StopReason.END_TURN);
    }
}
