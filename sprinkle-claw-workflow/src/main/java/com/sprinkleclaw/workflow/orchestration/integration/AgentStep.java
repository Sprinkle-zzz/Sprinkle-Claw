package com.sprinkleclaw.workflow.orchestration.integration;

import com.sprinkleclaw.workflow.orchestration.WorkflowStep;

import java.util.function.Function;

/**
 * 将声明式 Agent 方法包装为 {@link WorkflowStep}。
 *
 * <pre>{@code
 * var reviewer = AgentFactory.create(CodeReviewer.class, provider);
 * var step = AgentStep.fromAgent("review", reviewer::review);
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class AgentStep {

    private AgentStep() {}

    /**
     * 从 Agent 方法引用创建 WorkflowStep。
     */
    public static <I, O> WorkflowStep<I, O> fromAgent(String name, Function<I, O> agentMethod) {
        return WorkflowStep.of(name, agentMethod);
    }

    /**
     * 从 Agent 方法引用创建 WorkflowStep（默认名称 "agent"）。
     */
    public static <I, O> WorkflowStep<I, O> fromAgent(Function<I, O> agentMethod) {
        return WorkflowStep.of("agent", agentMethod);
    }
}
