package com.sprinkleclaw.workflow.agent;

import com.sprinkleclaw.llm.LlmCapabilities;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentFactoryTest {

    // ── 测试用接口 ──────────────────────────────────────────────

    @Agent(systemPrompt = "You are a helpful assistant.")
    interface SimpleAgent {
        String greet(String name);
    }

    @Agent(systemPrompt = "Return JSON.")
    interface StructuredAgent {
        Result analyze(String text);
    }

    record Result(String summary, int score) {}

    interface NotAnnotated {
        String hello(String x);
    }

    // ── 测试 ────────────────────────────────────────────────────

    @Test
    void create_simpleStringReturn_invokesLlmAndReturnsText() {
        LlmProvider mock = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("Hello, Alice!")),
                StopReason.END_TURN, new Usage(10, 5), "test-model");

        SimpleAgent agent = AgentFactory.create(SimpleAgent.class, mock);
        String result = agent.greet("Alice");
        assertThat(result).isEqualTo("Hello, Alice!");
    }

    @Test
    void create_structuredOutputPromptJson_parsesRecord() {
        // Mock LLM that returns well-formed JSON (PROMPT_JSON mode since no tool choice support)
        LlmProvider mock = new LlmProvider() {
            @Override
            public ChatResponse chat(com.sprinkleclaw.protocol.llm.ChatRequest request) {
                return new ChatResponse(
                        List.of(new ContentBlock.TextBlock("{\"summary\": \"good\", \"score\": 8}")),
                        StopReason.END_TURN, new Usage(10, 5), "test-model");
            }

            @Override
            public LlmCapabilities capabilities() {
                return LlmCapabilities.builder()
                        .supportsToolChoice(false)
                        .supportsToolUse(false)
                        .build();
            }
        };

        StructuredAgent agent = AgentFactory.create(StructuredAgent.class, mock);
        Result result = agent.analyze("sample text");
        assertThat(result.summary()).isEqualTo("good");
        assertThat(result.score()).isEqualTo(8);
    }

    @Test
    void create_nonInterface_throwsIllegalArgument() {
        LlmProvider mock = request -> ChatResponse.empty(StopReason.END_TURN);
        assertThatThrownBy(() -> AgentFactory.create(String.class, mock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an interface");
    }

    @Test
    void create_noAgentAnnotation_throwsIllegalArgument() {
        LlmProvider mock = request -> ChatResponse.empty(StopReason.END_TURN);
        assertThatThrownBy(() -> AgentFactory.create(NotAnnotated.class, mock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Agent");
    }

    @Test
    void create_objectMethods_delegatedCorrectly() {
        LlmProvider mock = request -> ChatResponse.empty(StopReason.END_TURN);
        SimpleAgent agent = AgentFactory.create(SimpleAgent.class, mock);
        assertThat(agent.toString()).contains("SimpleAgent");
        assertThat(agent.hashCode()).isNotZero();
        assertThat(agent.equals(agent)).isTrue();
    }

    @Test
    void create_correctionRetry_reissuesCallOnParseFailure() {
        AtomicInteger callCount = new AtomicInteger(0);
        LlmProvider mock = new LlmProvider() {
            @Override
            public ChatResponse chat(com.sprinkleclaw.protocol.llm.ChatRequest request) {
                int n = callCount.incrementAndGet();
                String content = n == 1
                        ? "Not valid JSON here"
                        : "{\"summary\": \"fixed\", \"score\": 5}";
                return new ChatResponse(
                        List.of(new ContentBlock.TextBlock(content)),
                        StopReason.END_TURN, new Usage(10, 5), "test-model");
            }

            @Override
            public LlmCapabilities capabilities() {
                return LlmCapabilities.builder()
                        .supportsToolChoice(false)
                        .supportsToolUse(false)
                        .build();
            }
        };

        StructuredAgent agent = AgentFactory.create(StructuredAgent.class, mock);
        Result result = agent.analyze("test");
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(result.summary()).isEqualTo("fixed");
    }
}
