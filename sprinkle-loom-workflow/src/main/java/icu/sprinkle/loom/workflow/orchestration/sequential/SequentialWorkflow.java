package icu.sprinkle.loom.workflow.orchestration.sequential;

import icu.sprinkle.loom.workflow.orchestration.*;
import icu.sprinkle.loom.workflow.orchestration.checkpoint.WorkflowCheckpoint;
import icu.sprinkle.loom.workflow.orchestration.checkpoint.WorkflowCheckpointStore;

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
    private final WorkflowCheckpointStore checkpointStore;

    public SequentialWorkflow(List<WorkflowStep<?, ?>> steps, ErrorPolicy errorPolicy) {
        this(steps, errorPolicy, null);
    }

    /**
     * 创建顺序 Workflow。
     *
     * @param steps 执行步骤列表
     * @param errorPolicy 错误处理策略
     * @param checkpointStore checkpoint 存储；为 {@code null} 时不保存 checkpoint
     */
    public SequentialWorkflow(List<WorkflowStep<?, ?>> steps,
                              ErrorPolicy errorPolicy,
                              WorkflowCheckpointStore checkpointStore) {
        this.steps = List.copyOf(steps);
        this.errorPolicy = errorPolicy;
        this.checkpointStore = checkpointStore;
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
                saveCheckpoint(ctx, step.name(), i, current);
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

    private void saveCheckpoint(WorkflowContext ctx, String stepName, int stepIndex, Object output) {
        if (checkpointStore == null) {
            return;
        }
        // checkpoint 保存发生在 stepResult 记录之后，确保快照能反映已完成步骤。
        checkpointStore.save(new WorkflowCheckpoint(
                ctx.workflowId(),
                stepName,
                stepIndex,
                output,
                ctx.attributesSnapshot(),
                ctx.stepResults(),
                Instant.now()));
    }

    @Override
    public String name() { return "sequential"; }
}
