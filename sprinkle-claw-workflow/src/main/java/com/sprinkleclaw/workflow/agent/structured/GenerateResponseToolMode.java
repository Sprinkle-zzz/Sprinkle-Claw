package com.sprinkleclaw.workflow.agent.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.llm.ChatResponse;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 通过强制工具调用实现可靠的结构化输出。
 * <p>注册一个 {@code generate_response} 特殊工具，其 inputSchema 为返回类型的 JSON Schema，
 * 然后用 {@code ToolChoice.Forced("generate_response")} 强制 LLM 必须通过工具调用返回结果。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class GenerateResponseToolMode {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static ChatRequest buildRequest(String userMessage, String systemPrompt,
                                           JsonNode returnSchema) {
        Map<String, Object> schemaMap = MAPPER.convertValue(returnSchema, Map.class);
        var generateTool = new ToolDefinition(
                "generate_response",
                "Generate the structured response. You MUST call this tool with the result.",
                schemaMap
        );
        return ChatRequest.builder()
                .systemPrompt(systemPrompt)
                .messages(List.of(Message.UserMessage.of(userMessage)))
                .tools(List.of(generateTool))
                .toolChoice(ChatRequest.ToolChoice.forced("generate_response"))
                .build();
    }

    public static <T> T extractResult(ChatResponse response, Type targetType) {
        var toolUse = response.content().stream()
                .filter(b -> b instanceof ContentBlock.ToolUseBlock)
                .map(b -> (ContentBlock.ToolUseBlock) b)
                .filter(b -> "generate_response".equals(b.name()))
                .findFirst()
                .orElseThrow(() -> new StructuredOutputException(
                        "LLM did not call generate_response tool"));
        return MAPPER.convertValue(toolUse.input(), MAPPER.constructType(targetType));
    }
}
