package icu.sprinkle.loom.workflow.agent.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import icu.sprinkle.loom.workflow.agent.Description;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 Java 类型生成 JSON Schema。
 * <p>支持基本类型、record、bean、泛型容器（List/Map/Optional）、{@link Description} 注解。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class JsonSchemaGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode fromType(Type type) {
        if (type instanceof ParameterizedType pt) {
            return fromParameterized(pt);
        }
        if (type instanceof Class<?> clazz) {
            return fromClass(clazz);
        }
        // fallback
        return MAPPER.createObjectNode().put("type", "object");
    }

    public static JsonNode fromClass(Class<?> clazz) {
        if (clazz == String.class || clazz == CharSequence.class) {
            return MAPPER.createObjectNode().put("type", "string");
        }
        if (clazz == int.class || clazz == Integer.class
                || clazz == long.class || clazz == Long.class
                || clazz == short.class || clazz == Short.class
                || clazz == byte.class || clazz == Byte.class) {
            return MAPPER.createObjectNode().put("type", "integer");
        }
        if (clazz == double.class || clazz == Double.class
                || clazz == float.class || clazz == Float.class) {
            return MAPPER.createObjectNode().put("type", "number");
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return MAPPER.createObjectNode().put("type", "boolean");
        }
        if (clazz.isEnum()) {
            ObjectNode schema = MAPPER.createObjectNode().put("type", "string");
            ArrayNode enumArray = schema.putArray("enum");
            for (Object c : clazz.getEnumConstants()) {
                enumArray.add(c.toString());
            }
            return schema;
        }
        if (clazz.isRecord()) {
            return fromRecord(clazz);
        }
        // general object
        return fromBean(clazz);
    }

    private static JsonNode fromParameterized(ParameterizedType pt) {
        Class<?> raw = (Class<?>) pt.getRawType();
        Type[] typeArgs = pt.getActualTypeArguments();

        if (List.class.isAssignableFrom(raw)) {
            ObjectNode schema = MAPPER.createObjectNode().put("type", "array");
            if (typeArgs.length > 0) {
                schema.set("items", fromType(typeArgs[0]));
            }
            return schema;
        }
        if (Map.class.isAssignableFrom(raw)) {
            ObjectNode schema = MAPPER.createObjectNode().put("type", "object");
            if (typeArgs.length > 1) {
                schema.set("additionalProperties", fromType(typeArgs[1]));
            }
            return schema;
        }
        if (Optional.class.isAssignableFrom(raw)) {
            if (typeArgs.length > 0) {
                return fromType(typeArgs[0]);
            }
        }
        // CompletableFuture<T> → schema of T
        if (java.util.concurrent.CompletableFuture.class.isAssignableFrom(raw)) {
            if (typeArgs.length > 0) {
                return fromType(typeArgs[0]);
            }
        }
        return fromClass(raw);
    }

    private static JsonNode fromRecord(Class<?> recordClass) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (RecordComponent comp : recordClass.getRecordComponents()) {
            JsonNode compSchema = fromType(comp.getGenericType());
            // @Description on record component
            Description desc = comp.getAnnotation(Description.class);
            if (desc != null && compSchema instanceof ObjectNode on) {
                on.put("description", desc.value());
            }
            props.set(comp.getName(), compSchema);
            required.add(comp.getName());
        }
        return schema;
    }

    private static JsonNode fromBean(Class<?> clazz) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (var field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) continue;

            JsonNode fieldSchema = fromType(field.getGenericType());
            Description desc = field.getAnnotation(Description.class);
            if (desc != null && fieldSchema instanceof ObjectNode on) {
                on.put("description", desc.value());
            }
            props.set(field.getName(), fieldSchema);
            required.add(field.getName());
        }
        return schema;
    }
}
