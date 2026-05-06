package icu.sprinkle.loom.workflow.orchestration.checkpoint;

import icu.sprinkle.loom.workflow.orchestration.StepResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Workflow 执行过程中的 checkpoint 快照。
 * <p>
 * 快照记录某个 step 成功完成后的输出、上下文属性和已完成 step 结果。
 * 该类型只定义 SDK 层数据契约，不假设具体持久化介质；数据库、Redis 或对象存储实现
 * 应由用户应用层通过 {@link WorkflowCheckpointStore} 自行接入。
 *
 * @param workflowId Workflow 上下文 ID
 * @param stepName 刚完成的 step 名称
 * @param stepIndex 刚完成的 step 序号
 * @param output step 输出
 * @param attributes 上下文属性快照
 * @param stepResults 已记录的 step 结果
 * @param createdAt checkpoint 创建时间
 *
 * @author sprinkle
 * @since 2026/5/6
 */
public record WorkflowCheckpoint(
        String workflowId,
        String stepName,
        int stepIndex,
        Object output,
        Map<String, Object> attributes,
        List<StepResult> stepResults,
        Instant createdAt
) {

    /**
     * 创建 Workflow checkpoint。
     */
    public WorkflowCheckpoint {
        attributes = Map.copyOf(attributes);
        stepResults = List.copyOf(stepResults);
    }
}
