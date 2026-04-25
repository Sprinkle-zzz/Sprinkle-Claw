package com.sprinkleclaw.core.eval;

import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEvaluatorTest {

    private Function<String, AgentResult> agentRunner;

    @BeforeEach
    void setUp() {
        agentRunner = input -> new AgentResult(
                "Agent response to: " + input,
                StopReason.END_TURN, List.of(), 1, new Usage(10, 20), Duration.ofMillis(100), List.of());
    }

    @Test
    void evaluate_allPass() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("PASS: greeting detected\nPASS: polite tone")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("greeting", "Hello!", "Responds with greeting", "Uses polite tone"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(1.0);
        assertThat(results.getFirst().passed()).containsExactly(true, true);
        assertThat(results.getFirst().scenario()).isEqualTo("greeting");
    }

    @Test
    void evaluate_partialPass() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("PASS: asked for order ID\nFAIL: did not show empathy")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("refund", "I want a refund", "Asks for order ID", "Shows empathy"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(0.5);
        assertThat(results.getFirst().passed()).containsExactly(true, false);
    }

    @Test
    void evaluate_multipleScenarios() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("PASS: correct")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("s1", "input1", "behavior1"),
                new EvalScenario("s2", "input2", "behavior2"),
                new EvalScenario("s3", "input3", "behavior3"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> r.score() == 1.0);
        assertThat(results.get(0).scenario()).isEqualTo("s1");
        assertThat(results.get(1).scenario()).isEqualTo("s2");
        assertThat(results.get(2).scenario()).isEqualTo("s3");
    }

    @Test
    void evaluate_agentThrows_scoreZero() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("PASS: ok")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        Function<String, AgentResult> failingRunner = input -> {
            throw new RuntimeException("Agent crashed");
        };

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("crash", "trigger", "Should not crash"));

        List<EvalResult> results = evaluator.evaluate(failingRunner, scenarios);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(0.0);
        assertThat(results.getFirst().feedback()).anyMatch(f -> f.contains("Agent execution failed"));
    }

    @Test
    void evaluate_emptyJudgeResponse_scoreZero() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("empty", "input", "some behavior"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(0.0);
        assertThat(results.getFirst().passed()).containsExactly(false);
    }

    @Test
    void evaluate_missingJudgmentLines_paddedWithFalse() {
        // Judge 只返回 1 个 PASS，但场景期望 3 个行为
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("PASS: first one ok")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("partial", "input", "b1", "b2", "b3"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().passed()).hasSize(3);
        assertThat(results.getFirst().passed()).containsExactly(true, false, false);
        assertThat(results.getFirst().score()).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void evaluate_noContentBlocks_emptyResponse() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(), StopReason.END_TURN, new Usage(0, 0), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(
                new EvalScenario("no-content", "input", "behavior"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(0.0);
    }

    @Test
    void evalScenario_varargsBehaviors() {
        var scenario = new EvalScenario("test", "input", "b1", "b2", "b3");
        assertThat(scenario.expectedBehaviors()).containsExactly("b1", "b2", "b3");
    }

    @Test
    void evalScenario_singleBehavior() {
        var scenario = new EvalScenario("test", "input", "only one");
        assertThat(scenario.expectedBehaviors()).containsExactly("only one");
    }

    @Test
    void evalResult_outputPreserved() {
        LlmProvider judgeLlm = request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("PASS: ok")),
                StopReason.END_TURN, new Usage(10, 20), "test-model");

        var evaluator = new AgentEvaluator(judgeLlm);
        var scenarios = List.of(new EvalScenario("out", "hello", "responds"));

        List<EvalResult> results = evaluator.evaluate(agentRunner, scenarios);

        assertThat(results.getFirst().output()).isEqualTo("Agent response to: hello");
    }
}
