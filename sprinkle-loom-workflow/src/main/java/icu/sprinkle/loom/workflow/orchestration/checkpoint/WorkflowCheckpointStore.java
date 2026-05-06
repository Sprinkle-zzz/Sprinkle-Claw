package icu.sprinkle.loom.workflow.orchestration.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Workflow checkpoint 存储 SPI。
 * <p>
 * SDK 只依赖该接口保存和读取 checkpoint。生产级持久化通常需要结合租户隔离、
 * TTL、加密和审计策略，因此不内置数据库实现。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
public interface WorkflowCheckpointStore {

    /**
     * 保存 checkpoint。
     *
     * @param checkpoint checkpoint 快照
     */
    void save(WorkflowCheckpoint checkpoint);

    /**
     * 读取指定 workflow 的最新 checkpoint。
     *
     * @param workflowId Workflow 上下文 ID
     * @return 最新 checkpoint；不存在时返回空
     */
    Optional<WorkflowCheckpoint> loadLatest(String workflowId);

    /**
     * 列出指定 workflow 的全部 checkpoint，按保存顺序返回。
     *
     * @param workflowId Workflow 上下文 ID
     * @return checkpoint 列表
     */
    List<WorkflowCheckpoint> list(String workflowId);

    /**
     * 删除指定 workflow 的全部 checkpoint。
     *
     * @param workflowId Workflow 上下文 ID
     */
    void delete(String workflowId);
}
