package com.sprinkleclaw.workflow.agent.structured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExtractorTest {

    @Test
    void extract_fencedJsonBlock_extractsContent() {
        String input = """
                Here is the result:
                ```json
                {"name": "Alice", "age": 30}
                ```
                Done.
                """;
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("{\"name\": \"Alice\", \"age\": 30}");
    }

    @Test
    void extract_bareJsonObject_extractsWholeString() {
        String input = "{\"key\": \"value\"}";
        assertThat(JsonExtractor.extract(input)).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extract_bareJsonArray_extractsWholeString() {
        String input = "[1, 2, 3]";
        assertThat(JsonExtractor.extract(input)).isEqualTo("[1, 2, 3]");
    }

    @Test
    void extract_textWithEmbeddedJson_findsJson() {
        String input = "The answer is: {\"result\": true} and that's it.";
        String json = JsonExtractor.extract(input);
        assertThat(json).contains("\"result\"");
    }

    @Test
    void extract_noJson_returnsNull() {
        assertThat(JsonExtractor.extract("Hello world, no json here")).isNull();
    }

    @Test
    void extract_nullInput_returnsNull() {
        assertThat(JsonExtractor.extract(null)).isNull();
    }

    @Test
    void extract_blankInput_returnsNull() {
        assertThat(JsonExtractor.extract("   ")).isNull();
    }

    @Test
    void extract_fencedWithoutJsonLabel_extractsContent() {
        String input = """
                ```
                {"status": "ok"}
                ```
                """;
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("{\"status\": \"ok\"}");
    }
}
