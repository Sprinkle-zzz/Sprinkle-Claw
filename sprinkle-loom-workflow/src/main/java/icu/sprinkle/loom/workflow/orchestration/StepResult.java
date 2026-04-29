package icu.sprinkle.loom.workflow.orchestration;

import java.time.Duration;
import java.time.Instant;

/**
 * 单步执行结果记录。
 *
 * @param stepName  步骤名称
 * @param output    步骤输出（成功时非 null）
 * @param duration  执行耗时
 * @param success   是否成功
 * @param error     失败时的异常（成功时 null）
 * @param startedAt 开始时间
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public record StepResult(
        String stepName,
        Object output,
        Duration duration,
        boolean success,
        Throwable error,
        Instant startedAt
) {
    public static StepResult success(String stepName, Object output, Duration duration, Instant startedAt) {
        return new StepResult(stepName, output, duration, true, null, startedAt);
    }

    public static StepResult failure(String stepName, Throwable error, Duration duration, Instant startedAt) {
        return new StepResult(stepName, null, duration, false, error, startedAt);
    }
}
