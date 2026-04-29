package icu.sprinkle.loom.llm.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaToolBridgeTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSchema(String json) {
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final ToolDefinition SAMPLE_TOOL = new ToolDefinition(
            "get_weather",
            "Get weather for a city",
            parseSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}")
    );

    @Test
    void toPromptTools_singleTool_containsNameAndSchema() {
        String prompt = OllamaToolBridge.toPromptTools(
                List.of(SAMPLE_TOOL), ChatRequest.ToolChoice.AUTO);
        assertThat(prompt).contains("get_weather");
        assertThat(prompt).contains("Get weather for a city");
        assertThat(prompt).contains("city");
    }

    @Test
    void toPromptTools_forcedChoice_containsMustCallInstruction() {
        String prompt = OllamaToolBridge.toPromptTools(
                List.of(SAMPLE_TOOL), ChatRequest.ToolChoice.forced("get_weather"));
        assertThat(prompt).contains("You MUST call the tool: get_weather");
    }

    @Test
    void toPromptTools_requiredChoice_containsMustCallAny() {
        String prompt = OllamaToolBridge.toPromptTools(
                List.of(SAMPLE_TOOL), ChatRequest.ToolChoice.REQUIRED);
        assertThat(prompt).contains("You MUST call at least one");
    }

    @Test
    void parseToolCallsFromText_fencedJson_extractsToolUseBlock() {
        String text = """
                Here is the result:
                ```json
                {"tool": "get_weather", "input": {"city": "Tokyo"}}
                ```
                Done.
                """;
        List<ContentBlock> blocks = OllamaToolBridge.parseToolCallsFromText(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst()).isInstanceOf(ContentBlock.ToolUseBlock.class);
        var toolUse = (ContentBlock.ToolUseBlock) blocks.getFirst();
        assertThat(toolUse.name()).isEqualTo("get_weather");
        assertThat(toolUse.input()).containsEntry("city", "Tokyo");
    }

    @Test
    void parseToolCallsFromText_bareJson_extractsToolUseBlock() {
        String text = """
                {"tool": "get_weather", "input": {"city": "London"}}
                """;
        List<ContentBlock> blocks = OllamaToolBridge.parseToolCallsFromText(text);
        assertThat(blocks).hasSize(1);
        var toolUse = (ContentBlock.ToolUseBlock) blocks.getFirst();
        assertThat(toolUse.name()).isEqualTo("get_weather");
    }

    @Test
    void parseToolCallsFromText_noToolCall_returnsEmpty() {
        List<ContentBlock> blocks = OllamaToolBridge.parseToolCallsFromText("Hello world, just text.");
        assertThat(blocks).isEmpty();
    }

    @Test
    void parseToolCallsFromText_malformedJson_returnsEmpty() {
        String text = """
                ```json
                {"tool": "get_weather", "input": {broken
                ```
                """;
        List<ContentBlock> blocks = OllamaToolBridge.parseToolCallsFromText(text);
        assertThat(blocks).isEmpty();
    }

    @Test
    void parseToolCallsFromText_multipleFencedBlocks_extractsAll() {
        String text = """
                ```json
                {"tool": "tool_a", "input": {"x": "1"}}
                ```
                Some text between.
                ```json
                {"tool": "tool_b", "input": {"y": "2"}}
                ```
                """;
        List<ContentBlock> blocks = OllamaToolBridge.parseToolCallsFromText(text);
        assertThat(blocks).hasSize(2);
        assertThat(((ContentBlock.ToolUseBlock) blocks.get(0)).name()).isEqualTo("tool_a");
        assertThat(((ContentBlock.ToolUseBlock) blocks.get(1)).name()).isEqualTo("tool_b");
    }
}
