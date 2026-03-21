package com.sprinkleclaw.tool;

import com.sprinkleclaw.tool.annotation.SchemaGenerator;
import com.sprinkleclaw.tool.annotation.Tool;
import com.sprinkleclaw.tool.annotation.ToolParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class SchemaGeneratorTest {

    @SuppressWarnings("unused")
    static class SampleTools {
        @Tool(description = "sample")
        public String greet(
                @ToolParam(name = "name", description = "Name to greet") String name,
                @ToolParam(name = "times", description = "Times", required = false) int times) {
            return "hi";
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSchema_createsCorrectSchema() throws Exception {
        Method method = SampleTools.class.getDeclaredMethod("greet", String.class, int.class);
        Map<String, Object> schema = SchemaGenerator.generateSchema(method);

        assertThat(schema.get("type")).isEqualTo("object");

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKeys("name", "times");

        Map<String, Object> nameSchema = (Map<String, Object>) props.get("name");
        assertThat(nameSchema.get("type")).isEqualTo("string");

        Map<String, Object> timesSchema = (Map<String, Object>) props.get("times");
        assertThat(timesSchema.get("type")).isEqualTo("integer");

        List<String> required = (List<String>) schema.get("required");
        assertThat(required).contains("name");
        assertThat(required).doesNotContain("times");
    }
}
