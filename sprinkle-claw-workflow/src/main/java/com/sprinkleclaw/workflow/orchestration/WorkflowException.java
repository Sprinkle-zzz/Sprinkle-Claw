package com.sprinkleclaw.workflow.orchestration;

/**
 * Workflow 执行异常，携带失败步骤名称和索引。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public class WorkflowException extends RuntimeException {

    private final String stepName;
    private final int stepIndex;

    public WorkflowException(String stepName, int stepIndex, Throwable cause) {
        super("Workflow failed at step '" + stepName + "' (index " + stepIndex + "): " + cause.getMessage(), cause);
        this.stepName = stepName;
        this.stepIndex = stepIndex;
    }

    public WorkflowException(String stepName, int stepIndex, String message) {
        super("Workflow failed at step '" + stepName + "' (index " + stepIndex + "): " + message);
        this.stepName = stepName;
        this.stepIndex = stepIndex;
    }

    public String stepName() { return stepName; }
    public int stepIndex() { return stepIndex; }
}
