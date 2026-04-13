package com.sprinkleclaw.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ollama 工具调用兼容层。
 * <p>不支持原生 tool_use 的模型走 prompt-based 降级：
 * 将工具定义注入 system prompt，并从模型文本输出中解析 JSON 工具调用。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class OllamaToolBridge {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 匹配 ```json ... ``` 或裸 JSON 块中的工具调用。
     */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\n?\\s*\\{\\s*\"tool\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"input\"\\s*:\\s*(\\{[^}]*\\})\\s*\\}\\s*\\n?```",
            Pattern.DOTALL
    );

    /**
     * 裸 JSON 块匹配。
     */
    private static final Pattern BARE_JSON_PATTERN = Pattern.compile(
            "\\{\\s*\"tool\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"input\"\\s*:\\s*(\\{[^}]*\\})\\s*\\}",
            Pattern.DOTALL
    );

    /**
     * 将工具定义转换为 prompt 模板（不支持 tool_use 时注入 system prompt）。
     */
    static String toPromptTools(List<ToolDefinition> tools, ChatRequest.ToolChoice choice) {
        var sb = new StringBuilder();
        sb.append("# Available Tools\n\n");
        for (var tool : tools) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append('\n');
            sb.append("  Schema: ").append(MAPPER.valueToTree(tool.inputSchema()).toString()).append('\n');
        }
        sb.append("\n# Tool Calling Protocol\n");
        sb.append("To call a tool, respond ONLY with a JSON block:\n");
        sb.append("```json\n{\"tool\": \"<name>\", \"input\": {...}}\n```\n");
        if (choice instanceof ChatRequest.ToolChoice.Forced f) {
            sb.append("You MUST call the tool: ").append(f.toolName()).append('\n');
        } else if (choice instanceof ChatRequest.ToolChoice.Required) {
            sb.append("You MUST call at least one of the available tools.\n");
        }
        return sb.toString();
    }

    /**
     * 从模型文本输出中解析工具调用 JSON 块。
     *
     * @return 解析出的 ToolUseBlock 列表（可能为空，表示没有工具调用）
     */
    @SuppressWarnings("unchecked")
    static List<ContentBlock> parseToolCallsFromText(String text) {
        List<ContentBlock> results = new ArrayList<>();

        // 先尝试 fenced JSON 块
        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            ContentBlock block = tryParseToolCall(matcher.group(1), matcher.group(2));
            if (block != null) results.add(block);
        }

        // 如果没找到 fenced，尝试裸 JSON
        if (results.isEmpty()) {
            matcher = BARE_JSON_PATTERN.matcher(text);
            while (matcher.find()) {
                ContentBlock block = tryParseToolCall(matcher.group(1), matcher.group(2));
                if (block != null) results.add(block);
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private static ContentBlock tryParseToolCall(String toolName, String inputJson) {
        try {
            JsonNode inputNode = MAPPER.readTree(inputJson);
            Map<String, Object> input = MAPPER.convertValue(inputNode, Map.class);
            String id = "ollama_prompt_" + toolName + "_" + System.nanoTime();
            return new ContentBlock.ToolUseBlock(id, toolName, input);
        } catch (Exception e) {
            return null;
        }
    }
}
