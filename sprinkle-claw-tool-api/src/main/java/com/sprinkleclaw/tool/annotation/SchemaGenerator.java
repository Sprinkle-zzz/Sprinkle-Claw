package com.sprinkleclaw.tool.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * JSON Schema 生成器，从 {@link Tool} 标注方法的参数中自动生成 JSON Schema。
 * <p>将 Java 类型映射为 JSON Schema 类型（如 String → "string"，int → "integer"）。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class SchemaGenerator {

    private SchemaGenerator() {
    }

    /**
     * 根据方法参数生成 JSON Schema（Map 形式）。
     * <p>遍历方法的所有参数，根据 {@link ToolParam} 注解提取描述和必填信息。</p>
     *
     * @param method 带有 {@link Tool} 注解的方法
     * @return JSON Schema 的 Map 表示
     */
    public static Map<String, Object> generateSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", mapJavaTypeToJsonType(param.getType()));

            String paramName = param.getName();
            if (toolParam != null) {
                if (!toolParam.name().isEmpty()) {
                    paramName = toolParam.name();
                }
                if (!toolParam.description().isEmpty()) {
                    prop.put("description", toolParam.description());
                }
                if (toolParam.required()) {
                    required.add(paramName);
                }
            } else {
                required.add(paramName);
            }

            properties.put(paramName, prop);
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return Collections.unmodifiableMap(schema);
    }

    /**
     * 将 Java 类型映射为 JSON Schema 类型字符串。
     */
    private static String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class) {
            return "integer";
        }
        if (type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class) {
            return "number";
        }
        if (type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isArray() || List.class.isAssignableFrom(type)) {
            return "array";
        }
        return "string";
    }
}
