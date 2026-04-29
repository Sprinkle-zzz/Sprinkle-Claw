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

    // ===== MVP10 栈式扫描鲁棒性 =====

    @Test
    void extract_jsonWithBracesInsideString_handledCorrectly() {
        // JSON 字符串字面量内的大括号不应该影响匹配
        String input = "Result: {\"template\": \"use {var} syntax\", \"valid\": true} done.";
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("{\"template\": \"use {var} syntax\", \"valid\": true}");
    }

    @Test
    void extract_jsonWithEscapedQuoteInString_handledCorrectly() {
        // 转义引号不应该让"内字符串模式"提前结束
        String input = "Result: {\"text\": \"say \\\"hi\\\" {today}\"} suffix";
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("{\"text\": \"say \\\"hi\\\" {today}\"}");
    }

    @Test
    void extract_multipleJsonBlocks_returnsFirstComplete() {
        String input = "First: {\"a\": 1} and second: {\"b\": 2}.";
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("{\"a\": 1}");
    }

    @Test
    void extract_nestedJsonObject_extractsOuterFully() {
        String input = "Wrap: {\"outer\": {\"inner\": {\"deep\": 42}}} end.";
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("{\"outer\": {\"inner\": {\"deep\": 42}}}");
    }

    @Test
    void extract_unbalancedBraces_returnsNull() {
        // 没有完整闭合的 JSON 应返回 null（避免错误抓到不完整片段）
        String input = "Broken: {\"a\": 1 missing close";
        assertThat(JsonExtractor.extract(input)).isNull();
    }

    @Test
    void extract_jsonArrayInText_extracted() {
        String input = "List: [{\"id\": 1}, {\"id\": 2}] count=2";
        String json = JsonExtractor.extract(input);
        assertThat(json).isEqualTo("[{\"id\": 1}, {\"id\": 2}]");
    }
}
