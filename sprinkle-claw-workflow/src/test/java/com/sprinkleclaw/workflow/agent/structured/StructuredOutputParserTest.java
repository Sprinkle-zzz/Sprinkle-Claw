package com.sprinkleclaw.workflow.agent.structured;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputParserTest {

    record PersonResult(String name, int age) {}

    private final JsonNode schema = JsonSchemaGenerator.fromClass(PersonResult.class);
    private final StructuredOutputParser<PersonResult> parser =
            new StructuredOutputParser<>(PersonResult.class, schema);

    @Test
    void parse_validJson_returnsSuccess() {
        ParseResult<PersonResult> result = parser.parse("{\"name\": \"Alice\", \"age\": 30}");
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var success = (ParseResult.Success<PersonResult>) result;
        assertThat(success.value().name()).isEqualTo("Alice");
        assertThat(success.value().age()).isEqualTo(30);
    }

    @Test
    void parse_noJson_returnsRetry() {
        ParseResult<PersonResult> result = parser.parse("Hello, no JSON here");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
    }

    @Test
    void parse_invalidJsonSyntax_returnsRetry() {
        ParseResult<PersonResult> result = parser.parse("{broken json");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
    }

    @Test
    void parse_missingRequiredField_returnsRetry() {
        ParseResult<PersonResult> result = parser.parse("{\"name\": \"Alice\"}");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
        var retry = (ParseResult.Retry<PersonResult>) result;
        assertThat(retry.correctionPrompt()).contains("age");
    }

    @Test
    void parse_wrongNodeType_returnsRetry() {
        ParseResult<PersonResult> result = parser.parse("[1, 2, 3]");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
    }

    @Test
    void parse_fencedJsonInText_extractsAndParses() {
        String input = """
                Here is the person:
                ```json
                {"name": "Bob", "age": 25}
                ```
                """;
        ParseResult<PersonResult> result = parser.parse(input);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var success = (ParseResult.Success<PersonResult>) result;
        assertThat(success.value().name()).isEqualTo("Bob");
    }

    @Test
    void parse_validJsonInSurroundingText_parses() {
        String input = "The result is: {\"name\": \"Carol\", \"age\": 40} and that's it.";
        ParseResult<PersonResult> result = parser.parse(input);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
    }
}
