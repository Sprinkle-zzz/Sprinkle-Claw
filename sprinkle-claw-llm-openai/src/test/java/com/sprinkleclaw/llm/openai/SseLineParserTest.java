package com.sprinkleclaw.llm.openai;

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
    void normalDataEvent() {
        parser.feed("data: {\"choices\":[]}");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("message");
        assertThat(event.isDone()).isFalse();
    }

    @Test
    void doneEventIsDetected() {
        parser.feed("data: [DONE]");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.isDone()).isTrue();
        assertThat(event.event()).isEqualTo("done");
    }

    @Test
    void doneWithWhitespace() {
        parser.feed("data:  [DONE] ");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.isDone()).isTrue();
    }

    @Test
    void multiLineData() {
        parser.feed("data: line1");
        parser.feed("data: line2");
        var event = parser.feed("");
        assertThat(event).isNotNull();
        assertThat(event.data()).isEqualTo("line1\nline2");
        assertThat(event.isDone()).isFalse();
    }

    @Test
    void commentIgnored() {
        parser.feed(": keep-alive");
        parser.feed("data: actual");
        var event = parser.feed("");
        assertThat(event.data()).isEqualTo("actual");
    }

    @Test
    void emptyLineWithoutDataReturnsNull() {
        assertThat(parser.feed("")).isNull();
    }

    @Test
    void consecutiveEvents() {
        parser.feed("data: first");
        var first = parser.feed("");
        assertThat(first.data()).isEqualTo("first");

        parser.feed("data: [DONE]");
        var done = parser.feed("");
        assertThat(done.isDone()).isTrue();
    }
}
