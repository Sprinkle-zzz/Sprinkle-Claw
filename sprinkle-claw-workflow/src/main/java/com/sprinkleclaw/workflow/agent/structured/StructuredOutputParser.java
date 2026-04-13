package com.sprinkleclaw.workflow.agent.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;

/**
 * 结构化输出解析器：从 LLM 文本输出中提取、校验并反序列化 JSON 对象。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class StructuredOutputParser<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Type targetType;
    private final JsonNode schema;

    public StructuredOutputParser(Type targetType, JsonNode schema) {
        this.targetType = targetType;
        this.schema = schema;
    }

    /**
     * 解析 LLM 输出文本为目标类型。
     */
    public ParseResult<T> parse(String llmOutput) {
        // 1. 提取 JSON
        String json = JsonExtractor.extract(llmOutput);
        if (json == null) {
            return ParseResult.retry(
                    "No JSON found in your response. Please respond with ONLY a valid JSON object matching the required schema.");
        }

        // 2. 解析 JSON
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            return ParseResult.retry(
                    "Invalid JSON syntax: " + e.getMessage()
                            + "\nPlease respond with a syntactically valid JSON object.");
        }

        // 3. 基础 schema 校验（类型匹配）
        if (schema != null) {
            String expectedType = schema.path("type").asText("");
            if ("object".equals(expectedType) && !node.isObject()) {
                return ParseResult.retry(
                        "Expected a JSON object but got " + node.getNodeType()
                                + ". Please respond with a JSON object.");
            }
            if ("array".equals(expectedType) && !node.isArray()) {
                return ParseResult.retry(
                        "Expected a JSON array but got " + node.getNodeType()
                                + ". Please respond with a JSON array.");
            }

            // 检查必填字段
            JsonNode requiredFields = schema.get("required");
            if (requiredFields != null && requiredFields.isArray() && node.isObject()) {
                for (JsonNode req : requiredFields) {
                    String fieldName = req.asText();
                    if (!node.has(fieldName)) {
                        return ParseResult.retry(
                                "Missing required field: '" + fieldName
                                        + "'. Please include all required fields in your JSON response.");
                    }
                }
            }
        }

        // 4. 反序列化
        try {
            @SuppressWarnings("unchecked")
            T value = (T) MAPPER.convertValue(node, MAPPER.constructType(targetType));
            return ParseResult.success(value);
        } catch (Exception e) {
            return ParseResult.retry(
                    "JSON structure doesn't match expected type: " + e.getMessage()
                            + "\nPlease ensure all fields have the correct types.");
        }
    }
}
