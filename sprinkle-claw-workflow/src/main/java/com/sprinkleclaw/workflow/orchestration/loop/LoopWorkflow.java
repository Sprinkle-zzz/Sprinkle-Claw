package com.sprinkleclaw.workflow.orchestration.loop;

import com.sprinkleclaw.workflow.orchestration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 循环编排：重复执行 body 直到终止条件满足或达到最大迭代次数。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class LoopWorkflow<I, O> implements Workflow<I, O> {

    private final WorkflowStep<I, O> body;
    private final Function<O, I> feedback;
    private final Predicate<O> terminationCondition;
    private final int maxIterations;

    public LoopWorkflow(WorkflowStep<I, O> body,
                        Function<O, I> feedback,
                        Predicate<O> terminationCondition,
                        int maxIterations) {
        this.body = body;
        this.feedback = feedback;
        this.terminationCondition = terminationCondition;
        this.maxIterations = maxIterations;
    }

    @Override
    public WorkflowResult<O> run(I input) {
        return run(input, null);
    }

    @Override
    public WorkflowResult<O> run(I input, WorkflowContext parentContext) {
        var ctx = WorkflowContext.createChild(parentContext, "loop");
        var start = Instant.now();
        var guard = new WorkflowLoopGuard();
        I current = input;

        for (int i = 0; i < maxIterations; i++) {
            ctx.throwIfCancelled();
            var stepStart = Instant.now();
            try {
                O output = body.execute(current, ctx);
                ctx.recordStep(StepResult.success(
                        body.name() + "[" + i + "]", output,
                        Duration.between(stepStart, Instant.now()), stepStart));

                if (terminationCondition.test(output)) {
                    return WorkflowResult.success(output, ctx.stepResults(),
                            Duration.between(start, Instant.now()));
                }
                guard.checkStagnation(output);
                current = feedback.apply(output);
            } catch (WorkflowException e) {
                throw e;
            } catch (Exception e) {
                ctx.recordStep(StepResult.failure(
                        body.name() + "[" + i + "]", e,
                        Duration.between(stepStart, Instant.now()), stepStart));
                var ex = new WorkflowException(body.name(), i, e);
                return WorkflowResult.failure(ex, ctx.stepResults(),
                        Duration.between(start, Instant.now()));
            }
        }

        var ex = new WorkflowException("loop", maxIterations,
                "Max iterations (" + maxIterations + ") exceeded without meeting termination condition");
        return WorkflowResult.failure(ex, ctx.stepResults(), Duration.between(start, Instant.now()));
    }

    @Override
    public String name() { return "loop"; }
}
