package com.sprinkleclaw.core.loop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工具输入参数的轻量级 JSON Schema 校验器。
 *
 * <p>在工具执行前验证 LLM 提供的参数是否满足 schema 约束，
 * 避免将无效参数传递给工具实现，减少无意义的执行开销。</p>
 *
 * <p>当前支持的校验项：
 * <ul>
 *   <li>必填字段（required）是否存在</li>
 *   <li>字段类型是否与 schema 声明一致</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public final class InputSchemaValidator {

    private InputSchemaValidator() {
    }

    /**
     * 校验输入参数是否符合 schema 约束。
     *
     * @param input       LLM 提供的参数
     * @param inputSchema 工具定义的 JSON Schema
     * @return 校验错误列表，为空表示校验通过
     */
    public static List<String> validate(Map<String, Object> input, Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> errors = new ArrayList<>();

        Object requiredObj = inputSchema.get("required");
        if (requiredObj instanceof List<?> requiredList) {
            for (Object r : requiredList) {
                String field = r.toString();
                if (!input.containsKey(field) || input.get(field) == null) {
                    errors.add("Missing required parameter: " + field);
                }
            }
        }

        Object propsObj = inputSchema.get("properties");
        if (propsObj instanceof Map<?, ?> properties) {
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }

                Object propDef = properties.get(key);
                if (propDef instanceof Map<?, ?> propMap) {
                    Object typeObj = propMap.get("type");
                    if (typeObj instanceof String expectedType) {
                        String actual = inferJsonType(value);
                        if (!isTypeCompatible(expectedType, actual)) {
                            errors.add("Parameter '" + key + "' expects type '"
                                    + expectedType + "' but got '" + actual + "'");
                        }
                    }
                }
            }
        }

        return errors;
    }

    private static String inferJsonType(Object value) {
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "integer";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof List<?>) {
            return "array";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        return "unknown";
    }

    /**
     * integer 与 number 兼容（JSON 中整数是 number 的子类型）。
     */
    private static boolean isTypeCompatible(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        if ("number".equals(expected) && "integer".equals(actual)) {
            return true;
        }
        return false;
    }
}
