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

    // ===== MVP10 扩展 schema 校验 =====

    @Test
    void parse_fieldTypeMismatch_returnsRetryWithPath() {
        // age 字段期望 integer 但收到 string
        ParseResult<PersonResult> result = parser.parse("{\"name\": \"Alice\", \"age\": \"thirty\"}");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
        var retry = (ParseResult.Retry<PersonResult>) result;
        assertThat(retry.correctionPrompt())
                .contains("$.age")
                .contains("integer")
                .contains("string");
    }

    record Address(String city, int zipCode) {}
    record Profile(String name, Address address) {}

    @Test
    void parse_nestedObjectFieldTypeMismatch_returnsRetryWithDeepPath() {
        JsonNode profileSchema = JsonSchemaGenerator.fromClass(Profile.class);
        StructuredOutputParser<Profile> profileParser =
                new StructuredOutputParser<>(Profile.class, profileSchema);

        // address.zipCode 期望 integer 但收到 string
        String input = "{\"name\": \"Bob\", \"address\": {\"city\": \"NYC\", \"zipCode\": \"10001\"}}";
        ParseResult<Profile> result = profileParser.parse(input);
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
        var retry = (ParseResult.Retry<Profile>) result;
        assertThat(retry.correctionPrompt())
                .contains("$.address.zipCode")
                .contains("integer");
    }

    @Test
    void parse_arrayItemTypeMismatch_returnsRetryWithIndexPath() {
        // 用手工 schema 构造 List<Integer>
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        var arraySchema = m.createObjectNode();
        arraySchema.put("type", "array");
        arraySchema.putObject("items").put("type", "integer");

        var arrayParser = new StructuredOutputParser<>(
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Integer>>() {}.getType(),
                arraySchema);
        ParseResult<?> result = arrayParser.parse("[1, \"two\", 3]");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
        var retry = (ParseResult.Retry<?>) result;
        assertThat(retry.correctionPrompt())
                .contains("$[1]")
                .contains("integer");
    }

    enum Color { RED, GREEN, BLUE }
    record ColoredItem(String name, Color color) {}

    @Test
    void parse_enumValueNotInList_returnsRetry() {
        JsonNode itemSchema = JsonSchemaGenerator.fromClass(ColoredItem.class);
        StructuredOutputParser<ColoredItem> itemParser =
                new StructuredOutputParser<>(ColoredItem.class, itemSchema);

        ParseResult<ColoredItem> result = itemParser.parse(
                "{\"name\": \"x\", \"color\": \"PURPLE\"}");
        assertThat(result).isInstanceOf(ParseResult.Retry.class);
        var retry = (ParseResult.Retry<ColoredItem>) result;
        assertThat(retry.correctionPrompt())
                .contains("PURPLE")
                .contains("enum");
    }

    @Test
    void parse_validNestedAndArray_succeeds() {
        JsonNode profileSchema = JsonSchemaGenerator.fromClass(Profile.class);
        StructuredOutputParser<Profile> profileParser =
                new StructuredOutputParser<>(Profile.class, profileSchema);

        String input = "{\"name\": \"Bob\", \"address\": {\"city\": \"NYC\", \"zipCode\": 10001}}";
        ParseResult<Profile> result = profileParser.parse(input);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var profile = ((ParseResult.Success<Profile>) result).value();
        assertThat(profile.name()).isEqualTo("Bob");
        assertThat(profile.address().city()).isEqualTo("NYC");
        assertThat(profile.address().zipCode()).isEqualTo(10001);
    }
}
