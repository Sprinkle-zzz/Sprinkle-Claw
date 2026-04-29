package icu.sprinkle.loom.workflow.orchestration.integration;

import icu.sprinkle.loom.workflow.orchestration.WorkflowContext;
import icu.sprinkle.loom.workflow.orchestration.WorkflowStep;

import java.util.function.Function;

/**
 * 将普通 {@link Function} 包装为 {@link WorkflowStep}。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class FunctionStep<I, O> implements WorkflowStep<I, O> {

    private final String stepName;
    private final Function<I, O> function;

    public FunctionStep(String name, Function<I, O> function) {
        this.stepName = name;
        this.function = function;
    }

    @Override
    public O execute(I input, WorkflowContext context) {
        return function.apply(input);
    }

    @Override
    public String name() { return stepName; }
}
