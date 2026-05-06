package icu.sprinkle.loom.workflow.agent;

import icu.sprinkle.loom.llm.LlmCapabilities;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
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

    @Agent(systemPromptResource = "prompts/agent-system.txt")
    interface ResourcePromptAgent {
        @SystemMessage(fromResource = "prompts/method-system.txt")
        @UserMessage(fromResource = "prompts/user-template.txt")
        String summarize(String topic);
    }

    @Agent
    interface ValidTemplateAgent {
        @UserMessage("Translate {text} to {language}")
        String translate(String text, String language);
    }

    @Agent(systemPrompt = {"Line one", "Line two"}, systemPromptDelimiter = " | ")
    interface MultiLinePromptAgent {
        @SystemMessage(value = {"Method line one", "Method line two"}, delimiter = " / ")
        @UserMessage(value = {"Question: {question}", "Style: {style}"}, delimiter = "\n---\n")
        String answer(String question, String style);
    }

    @Agent
    interface DynamicPromptAgent {
        String greet(String name);
    }

    @Agent
    interface UnusedParameterAgent {
        @UserMessage("Translate {text}")
        String unusedParameter(String text, String language);
    }

    @Agent
    interface UnknownVariableAgent {
        @UserMessage("Translate {text} to {locale}")
        String unknownVariable(String text, String language);
    }

    @Agent
    interface DuplicateVariableAgent {
        @UserMessage("Compare {text} with {text}")
        String duplicateVariable(String text);
    }

    record Result(String summary, int score) {
    }

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
            public ChatResponse chat(icu.sprinkle.loom.protocol.llm.ChatRequest request) {
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
    void create_resourcePrompts_loadsSystemAndUserTemplatesFromClasspath() {
        LlmProvider mock = request -> {
            assertThat(request.systemPrompt())
                    .contains("You are loaded from an agent resource.")
                    .contains("Use the method resource instructions.");
            assertThat(request.messages()).hasSize(1);
            assertThat(request.messages().getFirst())
                    .isInstanceOfSatisfying(Message.UserMessage.class, message ->
                            assertThat(message.content().getFirst())
                                    .isEqualTo(new ContentBlock.TextBlock("Summarize this topic: migration")));
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("ok")),
                    StopReason.END_TURN, new Usage(10, 5), "test-model");
        };

        ResourcePromptAgent agent = AgentFactory.create(ResourcePromptAgent.class, mock);
        assertThat(agent.summarize("migration")).isEqualTo("ok");
    }

    @Test
    void create_templatePrompt_rendersAllMethodParameters() {
        LlmProvider mock = request -> {
            assertThat(request.messages().getFirst())
                    .isInstanceOfSatisfying(Message.UserMessage.class, message ->
                            assertThat(message.content().getFirst())
                                    .isEqualTo(new ContentBlock.TextBlock("Translate hello to Chinese")));
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("你好")),
                    StopReason.END_TURN, new Usage(10, 5), "test-model");
        };

        ValidTemplateAgent agent = AgentFactory.create(ValidTemplateAgent.class, mock);
        assertThat(agent.translate("hello", "Chinese")).isEqualTo("你好");
    }

    @Test
    void create_multilinePrompt_joinsPromptPartsWithDelimiter() {
        LlmProvider mock = request -> {
            assertThat(request.systemPrompt())
                    .contains("Line one | Line two")
                    .contains("Method line one / Method line two");
            assertThat(request.messages().getFirst())
                    .isInstanceOfSatisfying(Message.UserMessage.class, message ->
                            assertThat(message.content().getFirst())
                                    .isEqualTo(new ContentBlock.TextBlock("Question: migration\n---\nStyle: concise")));
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("ok")),
                    StopReason.END_TURN, new Usage(10, 5), "test-model");
        };

        MultiLinePromptAgent agent = AgentFactory.create(MultiLinePromptAgent.class, mock);
        assertThat(agent.answer("migration", "concise")).isEqualTo("ok");
    }

    @Test
    void create_dynamicSystemPromptProvider_appliesWhenAnnotationsAreEmpty() {
        LlmProvider mock = request -> {
            assertThat(request.systemPrompt()).isEqualTo("Runtime prompt for Alice");
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("Hello")),
                    StopReason.END_TURN, new Usage(10, 5), "test-model");
        };
        AgentFactoryConfig config = AgentFactoryConfig.defaults()
                .llmProvider(mock)
                .dynamicSystemPromptProvider(context ->
                        "Runtime prompt for " + context.arguments().get("name"))
                .build();

        DynamicPromptAgent agent = AgentFactory.create(DynamicPromptAgent.class, config);
        assertThat(agent.greet("Alice")).isEqualTo("Hello");
    }

    @Test
    void create_dynamicSystemPromptProvider_doesNotOverrideAnnotationPrompt() {
        LlmProvider mock = request -> {
            assertThat(request.systemPrompt()).isEqualTo("You are a helpful assistant.");
            return new ChatResponse(
                    List.of(new ContentBlock.TextBlock("Hello")),
                    StopReason.END_TURN, new Usage(10, 5), "test-model");
        };
        AgentFactoryConfig config = AgentFactoryConfig.defaults()
                .llmProvider(mock)
                .dynamicSystemPromptProvider(context -> "Runtime prompt")
                .build();

        SimpleAgent agent = AgentFactory.create(SimpleAgent.class, config);
        assertThat(agent.greet("Alice")).isEqualTo("Hello");
    }

    @Test
    void create_templatePrompt_throwsWhenParameterIsUnused() {
        LlmProvider mock = request -> ChatResponse.empty(StopReason.END_TURN);

        assertThatThrownBy(() -> AgentFactory.create(UnusedParameterAgent.class, mock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not use prompt parameter(s): language");
    }

    @Test
    void create_templatePrompt_throwsWhenVariableIsUnknown() {
        LlmProvider mock = request -> ChatResponse.empty(StopReason.END_TURN);

        assertThatThrownBy(() -> AgentFactory.create(UnknownVariableAgent.class, mock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown prompt variable(s): locale");
    }

    @Test
    void create_templatePrompt_throwsWhenVariableIsDuplicated() {
        LlmProvider mock = request -> ChatResponse.empty(StopReason.END_TURN);

        assertThatThrownBy(() -> AgentFactory.create(DuplicateVariableAgent.class, mock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate prompt variable: text");
    }

    @Test
    void create_correctionRetry_reissuesCallOnParseFailure() {
        AtomicInteger callCount = new AtomicInteger(0);
        LlmProvider mock = new LlmProvider() {
            @Override
            public ChatResponse chat(icu.sprinkle.loom.protocol.llm.ChatRequest request) {
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
