package com.sprinkleclaw.llm.anthropic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseLineParserTest {

    private SseLineParser parser;

    @BeforeEach
    void setUp() {
        parser = new SseLineParser();
    }

    @Test
    void simpleEventWithTypeAndData() {
        assertThat(parser.feed("event: message_start")).isNull();
        assertThat(parser.feed("data: {\"type\":\"message_start\"}")).isNull();

        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("message_start");
        assertThat(event.data()).isEqualTo("{\"type\":\"message_start\"}");
    }

    @Test
    void defaultEventTypeIsMessage() {
        parser.feed("data: hello");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("message");
    }

    @Test
    void multiLineDataConcatenation() {
        parser.feed("data: line1");
        parser.feed("data: line2");
        parser.feed("data: line3");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("line1\nline2\nline3");
    }

    @Test
    void commentLinesAreIgnored() {
        parser.feed(": this is a comment");
        parser.feed("data: actual");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("actual");
    }

    @Test
    void emptyLineWithoutDataReturnsNull() {
        assertThat(parser.feed("")).isNull();
    }

    @Test
    void nullLineWithoutDataReturnsNull() {
        assertThat(parser.feed(null)).isNull();
    }

    @Test
    void resetClearsState() {
        parser.feed("event: content_block_delta");
        parser.feed("data: partial");
        parser.reset();

        parser.feed("data: fresh");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("message");
        assertThat(event.data()).isEqualTo("fresh");
    }

    @Test
    void fieldWithNoValueProducesEmptyString() {
        parser.feed("data:");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEmpty();
    }

    @Test
    void consecutiveEventsAreParsedIndependently() {
        parser.feed("event: ping");
        parser.feed("data: {}");
        var first = parser.feed("");
        assertThat(first.event()).isEqualTo("ping");

        parser.feed("event: message_delta");
        parser.feed("data: {\"delta\":{}}");
        var second = parser.feed("");
        assertThat(second.event()).isEqualTo("message_delta");
    }

    @Test
    void lineWithNoColonIsIgnored() {
        parser.feed("malformed-line");
        parser.feed("data: valid");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("valid");
    }

    @Test
    void dataValueWithColonInContent() {
        parser.feed("data: {\"url\":\"https://example.com\"}");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("{\"url\":\"https://example.com\"}");
    }
}
