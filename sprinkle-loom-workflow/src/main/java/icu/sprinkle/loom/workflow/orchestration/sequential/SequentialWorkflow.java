package icu.sprinkle.loom.workflow.orchestration.sequential;

import icu.sprinkle.loom.workflow.orchestration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 顺序编排：依次执行多个步骤，前一步输出作为后一步输入。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class SequentialWorkflow<I, O> implements Workflow<I, O> {

    private final List<WorkflowStep<?, ?>> steps;
    private final ErrorPolicy errorPolicy;

    public SequentialWorkflow(List<WorkflowStep<?, ?>> steps, ErrorPolicy errorPolicy) {
        this.steps = List.copyOf(steps);
        this.errorPolicy = errorPolicy;
    }

    @Override
    public WorkflowResult<O> run(I input) {
        return run(input, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public WorkflowResult<O> run(I input, WorkflowContext parentContext) {
        var ctx = WorkflowContext.createChild(parentContext, "sequential");
        var start = Instant.now();
        Object current = input;

        for (int i = 0; i < steps.size(); i++) {
            ctx.throwIfCancelled();
            var step = (WorkflowStep<Object, Object>) steps.get(i);
            var stepStart = Instant.now();
            try {
                current = step.execute(current, ctx);
                ctx.recordStep(StepResult.success(
                        step.name(), current, Duration.between(stepStart, Instant.now()), stepStart));
            } catch (Exception e) {
                ctx.recordStep(StepResult.failure(
                        step.name(), e, Duration.between(stepStart, Instant.now()), stepStart));
                if (errorPolicy == ErrorPolicy.FAIL_FAST) {
                    var ex = new WorkflowException(step.name(), i, e);
                    return WorkflowResult.failure(ex, ctx.stepResults(), Duration.between(start, Instant.now()));
                }
                // CONTINUE: current stays as previous value (may cause ClassCastException downstream)
            }
        }

        O finalOutput = (O) current;
        return WorkflowResult.success(finalOutput, ctx.stepResults(), Duration.between(start, Instant.now()));
    }

    @Override
    public String name() { return "sequential"; }
}
