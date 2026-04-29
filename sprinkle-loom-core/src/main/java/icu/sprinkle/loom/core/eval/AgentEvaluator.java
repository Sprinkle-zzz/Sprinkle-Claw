package icu.sprinkle.loom.core.eval;

import icu.sprinkle.loom.core.AgentResult;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Agent 质量评估框架（LLM-as-judge）。
 *
 * <p>对 Agent 运行一组测试场景，使用评估 LLM 检查输出是否满足期望行为。</p>
 *
 * <pre>
 * var evaluator = new AgentEvaluator(judgeLlm);
 * var results = evaluator.evaluate(
 *     claw::run,
 *     List.of(
 *         new EvalScenario("greeting", "Hello!", "Responds politely"),
 *         new EvalScenario("refund", "I want a refund", "Asks for order ID")
 *     )
 * );
 * </pre>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class AgentEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluator.class);

    private final LlmProvider judgeLlm;

    /**
     * @param judgeLlm 用于评判的 LLM（可以与被测 Agent 使用不同模型）
     */
    public AgentEvaluator(LlmProvider judgeLlm) {
        this.judgeLlm = judgeLlm;
    }

    /**
     * 对 Agent 运行一组评估场景。
     *
     * @param agentRunner 被测 Agent 的执行函数（输入 → 输出）
     * @param scenarios   评估场景列表
     * @return 评估结果列表
     */
    public List<EvalResult> evaluate(Function<String, AgentResult> agentRunner,
                                     List<EvalScenario> scenarios) {
        List<EvalResult> results = new ArrayList<>();
        for (EvalScenario scenario : scenarios) {
            log.info("评估场景: {}", scenario.name());
            try {
                AgentResult agentOutput = agentRunner.apply(scenario.input());
                EvalResult result = judge(scenario, agentOutput.output());
                results.add(result);
                log.info("  得分: {}/1.0", String.format("%.2f", result.score()));
            } catch (Exception e) {
                log.warn("  场景 {} 执行失败: {}", scenario.name(), e.getMessage());
                results.add(new EvalResult(
                        scenario.name(), 0.0,
                        scenario.expectedBehaviors().stream().map(b -> false).toList(),
                        List.of("Agent execution failed: " + e.getMessage()),
                        ""));
            }
        }
        return results;
    }

    private EvalResult judge(EvalScenario scenario, String agentOutput) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI evaluation judge. Evaluate the agent's output against the expected behaviors.\n\n");
        prompt.append("User Input: ").append(scenario.input()).append("\n\n");
        prompt.append("Agent Output: ").append(agentOutput).append("\n\n");
        prompt.append("Expected Behaviors:\n");
        for (int i = 0; i < scenario.expectedBehaviors().size(); i++) {
            prompt.append(i + 1).append(". ").append(scenario.expectedBehaviors().get(i)).append("\n");
        }
        prompt.append("\nFor each expected behavior, respond with PASS or FAIL followed by a brief reason.");
        prompt.append("\nFormat: one line per behavior, e.g. 'PASS: correctly asked for order ID'");

        var request = ChatRequest.builder()
                .messages(List.of(Message.UserMessage.of(prompt.toString())))
                .maxTokens(1000)
                .build();

        ChatResponse response = judgeLlm.chat(request);
        String judgeOutput = extractText(response);

        return parseJudgment(scenario, judgeOutput, agentOutput);
    }

    private EvalResult parseJudgment(EvalScenario scenario, String judgeOutput, String agentOutput) {
        String[] lines = judgeOutput.split("\n");
        List<Boolean> passed = new ArrayList<>();
        List<String> feedback = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toUpperCase().startsWith("PASS")) {
                passed.add(true);
                feedback.add(trimmed);
            } else if (trimmed.toUpperCase().startsWith("FAIL")) {
                passed.add(false);
                feedback.add(trimmed);
            }
        }

        while (passed.size() < scenario.expectedBehaviors().size()) {
            passed.add(false);
            feedback.add("No judgment provided");
        }

        double score = passed.isEmpty() ? 0.0 :
                passed.stream().filter(p -> p).count() / (double) passed.size();

        return new EvalResult(scenario.name(), score, passed, feedback, agentOutput);
    }

    private String extractText(ChatResponse response) {
        if (response.content() == null || response.content().isEmpty()) {
            return "";
        }
        for (ContentBlock block : response.content()) {
            if (block instanceof ContentBlock.TextBlock tb) {
                return tb.text();
            }
        }
        return "";
    }
}
