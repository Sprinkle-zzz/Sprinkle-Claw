package com.sprinkleclaw.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.llm.LlmCapabilities;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")

class OllamaProtocolMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private OllamaProtocolMapper mapper;
    private OllamaConfig config;

    private static final LlmCapabilities TOOL_CAPABLE = LlmCapabilities.builder()
            .supportsToolUse(true).supportsToolChoice(true).supportsStreaming(true)
            .contextWindowTokens(128_000).maxOutputTokens(8192).build();

    private static final LlmCapabilities NO_TOOL = LlmCapabilities.builder()
            .supportsToolUse(false).supportsToolChoice(false).supportsStreaming(true)
            .contextWindowTokens(8_192).maxOutputTokens(2048).build();

    @BeforeEach
    void setUp() {
        mapper = new OllamaProtocolMapper();
        config = OllamaConfig.builder().model("llama3.1:8b")
                .keepAlive(Duration.ofMinutes(5)).build();
    }

    @Test
    void toOllamaRequest_basicChat_containsModelAndMessages() throws Exception {
        ChatRequest req = ChatRequest.builder()
                .systemPrompt("You are helpful.")
                .messages(List.of(Message.UserMessage.of("Hello")))
                .build();
        String json = mapper.toOllamaRequest(req, config, TOOL_CAPABLE, false);
        JsonNode root = JSON.readTree(json);
        assertThat(root.path("model").asText()).isEqualTo("llama3.1:8b");
        assertThat(root.path("stream").asBoolean()).isFalse();
        assertThat(root.path("messages")).isNotEmpty();
        assertThat(root.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(root.path("messages").get(1).path("role").asText()).isEqualTo("user");
    }

    private static Map<String, Object> parseSchema(String json) {
        try {
            return JSON.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void toOllamaRequest_withTools_nativeSupport_includesToolsArray() throws Exception {
        var tool = new ToolDefinition("read_file", "Read a file",
                parseSchema("{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}"));
        ChatRequest req = ChatRequest.builder()
                .systemPrompt("sys")
                .messages(List.of(Message.UserMessage.of("read it")))
                .tools(List.of(tool))
                .build();
        String json = mapper.toOllamaRequest(req, config, TOOL_CAPABLE, false);
        JsonNode root = JSON.readTree(json);
        assertThat(root.has("tools")).isTrue();
        assertThat(root.path("tools").get(0).path("function").path("name").asText()).isEqualTo("read_file");
    }

    @Test
    void toOllamaRequest_withTools_noNativeSupport_injectsPromptInSystem() throws Exception {
        var tool = new ToolDefinition("bash", "Run command",
                parseSchema("{\"type\":\"object\",\"properties\":{\"cmd\":{\"type\":\"string\"}}}"));
        ChatRequest req = ChatRequest.builder()
                .systemPrompt("Base prompt")
                .messages(List.of(Message.UserMessage.of("run ls")))
                .tools(List.of(tool))
                .build();
        String json = mapper.toOllamaRequest(req, config, NO_TOOL, false);
        JsonNode root = JSON.readTree(json);
        assertThat(root.has("tools")).isFalse();
        String sysContent = root.path("messages").get(0).path("content").asText();
        assertThat(sysContent).contains("bash");
        assertThat(sysContent).contains("Available Tools");
    }

    @Test
    void toOllamaRequest_options_temperatureAndMaxTokens() throws Exception {
        ChatRequest req = ChatRequest.builder()
                .systemPrompt("sys")
                .messages(List.of(Message.UserMessage.of("hi")))
                .maxTokens(1024)
                .temperature(0.5)
                .build();
        String json = mapper.toOllamaRequest(req, config, TOOL_CAPABLE, false);
        JsonNode root = JSON.readTree(json);
        assertThat(root.path("options").path("num_predict").asInt()).isEqualTo(1024);
        assertThat(root.path("options").path("temperature").asDouble()).isEqualTo(0.5);
    }

    @Test
    void toOllamaRequest_keepAlive_serialized() throws Exception {
        ChatRequest req = ChatRequest.builder()
                .systemPrompt("sys")
                .messages(List.of(Message.UserMessage.of("hi")))
                .build();
        String json = mapper.toOllamaRequest(req, config, TOOL_CAPABLE, false);
        JsonNode root = JSON.readTree(json);
        assertThat(root.path("keep_alive").asText()).isEqualTo("300s");
    }

    @Test
    void toChatResponse_textOnly_parsesCorrectly() throws Exception {
        String responseJson = """
                {
                  "model": "llama3.1:8b",
                  "message": {"role": "assistant", "content": "Hello!"},
                  "done": true,
                  "prompt_eval_count": 10,
                  "eval_count": 5
                }
                """;
        ChatResponse resp = mapper.toChatResponse(responseJson, TOOL_CAPABLE);
        assertThat(resp.textContent()).isEqualTo("Hello!");
        assertThat(resp.stopReason()).isEqualTo(StopReason.END_TURN);
        assertThat(resp.usage().inputTokens()).isEqualTo(10);
        assertThat(resp.usage().outputTokens()).isEqualTo(5);
        assertThat(resp.modelId()).isEqualTo("llama3.1:8b");
    }

    @Test
    void toChatResponse_withNativeToolCalls_parsesToolUseBlocks() throws Exception {
        String responseJson = """
                {
                  "model": "llama3.1:8b",
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "tool_calls": [{
                      "function": {
                        "name": "get_weather",
                        "arguments": {"city": "Paris"}
                      }
                    }]
                  },
                  "done": true,
                  "prompt_eval_count": 15,
                  "eval_count": 8
                }
                """;
        ChatResponse resp = mapper.toChatResponse(responseJson, TOOL_CAPABLE);
        assertThat(resp.toolCalls()).hasSize(1);
        assertThat(resp.toolCalls().getFirst().name()).isEqualTo("get_weather");
        assertThat(resp.toolCalls().getFirst().input()).containsEntry("city", "Paris");
        assertThat(resp.stopReason()).isEqualTo(StopReason.TOOL_USE);
    }

    @Test
    void toChatResponse_noToolSupport_extractsFromText() throws Exception {
        String responseJson = """
                {
                  "model": "mistral",
                  "message": {
                    "role": "assistant",
                    "content": "```json\\n{\\"tool\\": \\"bash\\", \\"input\\": {\\"cmd\\": \\"ls\\"}}\\n```"
                  },
                  "done": true,
                  "prompt_eval_count": 10,
                  "eval_count": 12
                }
                """;
        ChatResponse resp = mapper.toChatResponse(responseJson, NO_TOOL);
        assertThat(resp.toolCalls()).hasSize(1);
        assertThat(resp.toolCalls().getFirst().name()).isEqualTo("bash");
        assertThat(resp.stopReason()).isEqualTo(StopReason.TOOL_USE);
    }
}
