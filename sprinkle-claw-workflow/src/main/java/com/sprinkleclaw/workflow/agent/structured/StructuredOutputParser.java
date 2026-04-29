package com.sprinkleclaw.workflow.agent.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化输出解析器：从 LLM 文本输出中提取、校验并反序列化 JSON 对象。
 *
 * <h3>校验链</h3>
 * <ol>
 *   <li>JSON 提取（{@link JsonExtractor}）</li>
 *   <li>JSON 语法解析</li>
 *   <li>Schema 递归校验：根类型 / 必填字段 / 字段类型 / 数组 items / 嵌套对象 / enum</li>
 *   <li>反序列化为目标类型</li>
 * </ol>
 *
 * <p>任一校验步失败返回 {@link ParseResult.Retry}，附带具体路径的 correction prompt
 * 引导 LLM 自纠正（如 "Field 'address.city' expected string but got integer"）。</p>
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

        // 3. 递归 Schema 校验
        if (schema != null) {
            String error = validateAgainstSchema(node, schema, "$");
            if (error != null) {
                return ParseResult.retry(error
                        + "\nPlease fix the response and return ONLY the corrected JSON.");
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

    /**
     * 递归校验节点是否符合 schema，返回 {@code null} 表示通过，否则返回错误描述。
     *
     * @param node   被校验节点
     * @param schema 期望 schema
     * @param path   JSONPath 风格的路径（用于错误提示，如 {@code $.user.address.city}）
     */
    private static String validateAgainstSchema(JsonNode node, JsonNode schema, String path) {
        String expectedType = schema.path("type").asText("");

        // 类型校验
        String typeError = validateType(node, expectedType, path);
        if (typeError != null) {
            return typeError;
        }

        // enum 校验（仅对原始类型）
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            String enumError = validateEnum(node, enumNode, path);
            if (enumError != null) {
                return enumError;
            }
        }

        // 对象：必填字段 + properties 递归
        if ("object".equals(expectedType) && node.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                List<String> missing = new ArrayList<>();
                for (JsonNode r : required) {
                    if (!node.has(r.asText())) {
                        missing.add(r.asText());
                    }
                }
                if (!missing.isEmpty()) {
                    return "Missing required field(s) at " + path + ": " + missing;
                }
            }
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                var fieldNames = properties.fieldNames();
                while (fieldNames.hasNext()) {
                    String name = fieldNames.next();
                    if (node.has(name)) {
                        String childError = validateAgainstSchema(
                                node.get(name), properties.get(name), path + "." + name);
                        if (childError != null) {
                            return childError;
                        }
                    }
                }
            }
        }

        // 数组：items 递归
        if ("array".equals(expectedType) && node.isArray()) {
            JsonNode items = schema.get("items");
            if (items != null) {
                for (int i = 0; i < node.size(); i++) {
                    String childError = validateAgainstSchema(
                            node.get(i), items, path + "[" + i + "]");
                    if (childError != null) {
                        return childError;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 校验节点类型与 schema 期望类型是否匹配。
     */
    private static String validateType(JsonNode node, String expectedType, String path) {
        if (expectedType.isEmpty()) {
            return null;
        }
        boolean ok = switch (expectedType) {
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "string" -> node.isTextual();
            // JSON Schema 允许 integer 是 number 的子类，反之不允许；Jackson isInt() 仅在无小数点时为 true
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "null" -> node.isNull();
            default -> true; // 未知类型不阻断
        };
        if (!ok) {
            return "Field at " + path + " expected " + expectedType + " but got "
                    + describeNodeType(node);
        }
        return null;
    }

    private static String validateEnum(JsonNode node, JsonNode enumValues, String path) {
        for (JsonNode allowed : enumValues) {
            if (node.equals(allowed)) {
                return null;
            }
        }
        List<String> allowedList = new ArrayList<>();
        for (JsonNode allowed : enumValues) {
            allowedList.add(allowed.asText());
        }
        return "Field at " + path + " value '" + node.asText()
                + "' is not in allowed enum values: " + allowedList;
    }

    private static String describeNodeType(JsonNode node) {
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isFloatingPointNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isNull()) {
            return "null";
        }
        return node.getNodeType().toString().toLowerCase();
    }
}
