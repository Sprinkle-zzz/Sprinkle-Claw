package icu.sprinkle.loom.workflow.orchestration.checkpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的 Workflow checkpoint 存储实现。
 * <p>
 * 该实现适合测试、本地调试和示例，进程退出后数据会丢失。生产环境应实现
 * {@link WorkflowCheckpointStore} 接入应用自己的持久化系统。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
public final class InMemoryWorkflowCheckpointStore implements WorkflowCheckpointStore {

    private final ConcurrentMap<String, List<WorkflowCheckpoint>> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowCheckpoint checkpoint) {
        checkpoints.compute(checkpoint.workflowId(), (ignored, existing) -> {
            List<WorkflowCheckpoint> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            updated.add(checkpoint);
            return List.copyOf(updated);
        });
    }

    @Override
    public Optional<WorkflowCheckpoint> loadLatest(String workflowId) {
        List<WorkflowCheckpoint> values = checkpoints.get(workflowId);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.getLast());
    }

    @Override
    public List<WorkflowCheckpoint> list(String workflowId) {
        return checkpoints.getOrDefault(workflowId, List.of());
    }

    @Override
    public void delete(String workflowId) {
        checkpoints.remove(workflowId);
    }
}
