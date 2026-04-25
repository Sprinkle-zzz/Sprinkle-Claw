package com.sprinkleclaw.core.eval;

import java.util.List;

/**
 * Agent 评估场景。
 *
 * @param name              场景名称
 * @param input             用户输入消息
 * @param expectedBehaviors 期望行为描述列表（供评估 LLM 判断）
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public record EvalScenario(
        String name,
        String input,
        List<String> expectedBehaviors
) {
    public EvalScenario(String name, String input, String... expectedBehaviors) {
        this(name, input, List.of(expectedBehaviors));
    }
}
