package icu.sprinkle.loom.workflow.orchestration;

import java.time.Duration;
import java.util.List;

/**
 * Workflow 执行结果，包含输出、各步骤结果和总耗时。
 *
 * @param output       最终输出
 * @param stepResults  各步骤执行记录
 * @param totalDuration 总耗时
 * @param success      是否成功
 * @param error        失败时的异常（成功时 null）
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public record WorkflowResult<O>(
        O output,
        List<StepResult> stepResults,
        Duration totalDuration,
        boolean success,
        WorkflowException error
) {
    public static <O> WorkflowResult<O> success(O output, List<StepResult> stepResults, Duration duration) {
        return new WorkflowResult<>(output, List.copyOf(stepResults), duration, true, null);
    }

    public static <O> WorkflowResult<O> failure(WorkflowException error, List<StepResult> stepResults, Duration duration) {
        return new WorkflowResult<>(null, List.copyOf(stepResults), duration, false, error);
    }
}
