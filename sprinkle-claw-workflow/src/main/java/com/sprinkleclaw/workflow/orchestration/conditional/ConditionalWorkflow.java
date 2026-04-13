package com.sprinkleclaw.workflow.orchestration.conditional;

import com.sprinkleclaw.workflow.orchestration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * 条件编排：根据谓词选择 then 分支或 otherwise 分支执行。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class ConditionalWorkflow<I, O> implements Workflow<I, O> {

    private final Predicate<I> predicate;
    private final WorkflowStep<I, O> thenBranch;
    private final WorkflowStep<I, O> otherwiseBranch;

    public ConditionalWorkflow(Predicate<I> predicate,
                               WorkflowStep<I, O> thenBranch,
                               WorkflowStep<I, O> otherwiseBranch) {
        this.predicate = predicate;
        this.thenBranch = thenBranch;
        this.otherwiseBranch = otherwiseBranch;
    }

    @Override
    public WorkflowResult<O> run(I input) {
        return run(input, null);
    }

    @Override
    public WorkflowResult<O> run(I input, WorkflowContext parentContext) {
        var ctx = WorkflowContext.createChild(parentContext, "conditional");
        var start = Instant.now();

        WorkflowStep<I, O> chosen = predicate.test(input) ? thenBranch : otherwiseBranch;
        var stepStart = Instant.now();
        try {
            O output = chosen.execute(input, ctx);
            ctx.recordStep(StepResult.success(
                    chosen.name(), output,
                    Duration.between(stepStart, Instant.now()), stepStart));
            return WorkflowResult.success(output, ctx.stepResults(),
                    Duration.between(start, Instant.now()));
        } catch (Exception e) {
            ctx.recordStep(StepResult.failure(
                    chosen.name(), e,
                    Duration.between(stepStart, Instant.now()), stepStart));
            var ex = new WorkflowException(chosen.name(), 0, e);
            return WorkflowResult.failure(ex, ctx.stepResults(),
                    Duration.between(start, Instant.now()));
        }
    }

    @Override
    public String name() { return "conditional"; }
}
