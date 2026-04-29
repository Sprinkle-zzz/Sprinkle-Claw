package icu.sprinkle.loom.workflow.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Workflow 执行上下文：共享状态、取消信号、步骤结果记录。
 * <p>支持嵌套：子 context 继承父级的取消信号。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class WorkflowContext {

    private final String workflowId;
    private final Instant startTime;
    private final WorkflowContext parent;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<StepResult> stepResults = new CopyOnWriteArrayList<>();

    private WorkflowContext(String workflowId, WorkflowContext parent) {
        this.workflowId = workflowId;
        this.startTime = Instant.now();
        this.parent = parent;
    }

    /**
     * 创建根 context。
     */
    public static WorkflowContext create() {
        return new WorkflowContext(UUID.randomUUID().toString(), null);
    }

    /**
     * 创建子 context（嵌套 Workflow 使用）。
     */
    public static WorkflowContext createChild(WorkflowContext parent, String stepName) {
        if (parent == null) {
            return create();
        }
        return new WorkflowContext(parent.workflowId + "/" + stepName, parent);
    }

    public String workflowId() { return workflowId; }
    public Instant startTime() { return startTime; }

    @SuppressWarnings("unchecked")
    public <V> V getAttribute(String key, Class<V> type) {
        return (V) attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get() || (parent != null && parent.isCancelled());
    }

    /**
     * 检查取消状态，已取消则抛异常。供 step 在长耗时操作中调用。
     */
    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new WorkflowException("cancelled", -1, "Workflow was cancelled");
        }
    }

    public List<StepResult> stepResults() {
        return List.copyOf(stepResults);
    }

    public void recordStep(StepResult result) {
        stepResults.add(result);
    }
}
