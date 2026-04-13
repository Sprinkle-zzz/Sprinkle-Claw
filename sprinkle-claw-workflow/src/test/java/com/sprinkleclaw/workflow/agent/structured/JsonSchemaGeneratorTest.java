package com.sprinkleclaw.workflow.agent.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprinkleclaw.workflow.agent.Description;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaGeneratorTest {

    // ── 测试用类型 ──────────────────────────────────────────

    enum Priority { LOW, MEDIUM, HIGH }

    record PersonResult(String name, int age) {}

    record Nested(String label, PersonResult person) {}

    record WithDescription(@Description("The user's name") String name) {}

    // ── 测试 ────────────────────────────────────────────────

    @Test
    void fromType_string_returnsStringSchema() {
        JsonNode schema = JsonSchemaGenerator.fromClass(String.class);
        assertThat(schema.path("type").asText()).isEqualTo("string");
    }

    @Test
    void fromType_integer_returnsIntegerSchema() {
        assertThat(JsonSchemaGenerator.fromClass(int.class).path("type").asText()).isEqualTo("integer");
        assertThat(JsonSchemaGenerator.fromClass(Integer.class).path("type").asText()).isEqualTo("integer");
        assertThat(JsonSchemaGenerator.fromClass(long.class).path("type").asText()).isEqualTo("integer");
    }

    @Test
    void fromType_double_returnsNumberSchema() {
        assertThat(JsonSchemaGenerator.fromClass(double.class).path("type").asText()).isEqualTo("number");
        assertThat(JsonSchemaGenerator.fromClass(Float.class).path("type").asText()).isEqualTo("number");
    }

    @Test
    void fromType_boolean_returnsBooleanSchema() {
        assertThat(JsonSchemaGenerator.fromClass(boolean.class).path("type").asText()).isEqualTo("boolean");
    }

    @Test
    void fromType_enum_returnsStringWithEnumValues() {
        JsonNode schema = JsonSchemaGenerator.fromClass(Priority.class);
        assertThat(schema.path("type").asText()).isEqualTo("string");
        assertThat(schema.path("enum")).isNotEmpty();
        assertThat(schema.path("enum").get(0).asText()).isEqualTo("LOW");
        assertThat(schema.path("enum").get(2).asText()).isEqualTo("HIGH");
    }

    @Test
    void fromType_record_returnsObjectWithProperties() {
        JsonNode schema = JsonSchemaGenerator.fromClass(PersonResult.class);
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").has("name")).isTrue();
        assertThat(schema.path("properties").path("name").path("type").asText()).isEqualTo("string");
        assertThat(schema.path("properties").path("age").path("type").asText()).isEqualTo("integer");
        assertThat(schema.path("required")).isNotEmpty();
    }

    @Test
    void fromType_nestedRecord_generatesNestedSchema() {
        JsonNode schema = JsonSchemaGenerator.fromClass(Nested.class);
        assertThat(schema.path("properties").path("person").path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").path("person").path("properties").has("name")).isTrue();
    }

    @Test
    void fromType_listOfString_returnsArraySchema() throws Exception {
        // Use a field to get the generic type
        Type listType = getGenericFieldType("stringList");
        JsonNode schema = JsonSchemaGenerator.fromType(listType);
        assertThat(schema.path("type").asText()).isEqualTo("array");
        assertThat(schema.path("items").path("type").asText()).isEqualTo("string");
    }

    @Test
    void fromType_mapOfStringToInt_returnsObjectWithAdditionalProps() throws Exception {
        Type mapType = getGenericFieldType("stringIntMap");
        JsonNode schema = JsonSchemaGenerator.fromType(mapType);
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").path("type").asText()).isEqualTo("integer");
    }

    @Test
    void fromType_recordWithDescription_includesDescription() {
        JsonNode schema = JsonSchemaGenerator.fromClass(WithDescription.class);
        assertThat(schema.path("properties").path("name").path("description").asText())
                .isEqualTo("The user's name");
    }

    // ── 用于获取泛型 Type 的辅助字段 ──────────────────────

    @SuppressWarnings("unused")
    private List<String> stringList;

    @SuppressWarnings("unused")
    private Map<String, Integer> stringIntMap;

    private Type getGenericFieldType(String fieldName) throws NoSuchFieldException {
        return getClass().getDeclaredField(fieldName).getGenericType();
    }
}
