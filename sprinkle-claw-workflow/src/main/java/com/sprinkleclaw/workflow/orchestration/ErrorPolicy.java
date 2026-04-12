package com.sprinkleclaw.workflow.orchestration;

/**
 * Workflow 错误处理策略。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public enum ErrorPolicy {

    /** 任一 step 失败立即终止，抛 WorkflowException */
    FAIL_FAST,

    /** 失败记录为 StepResult.error 但继续后续 step */
    CONTINUE,

    /** 失败重试（默认 maxRetries=2） */
    RETRY
}
