package icu.sprinkle.loom.workflow.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 从 checkpoint 快照恢复 Workflow 上下文。
     * <p>
     * 恢复出的 context 使用传入的 workflowId、attributes 和 stepResults，但不会恢复
     * parent 或取消状态。startTime 使用当前时间，表示本次 resume 的开始时间。
     * attributes 是浅拷贝，属性值对象本身是否可变由调用方控制。
     *
     * @param workflowId Workflow 上下文 ID
     * @param attributes 上下文属性快照
     * @param stepResults 已完成步骤记录
     * @return 恢复后的 Workflow 上下文
     */
    public static WorkflowContext restore(String workflowId,
                                          Map<String, Object> attributes,
                                          List<StepResult> stepResults) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        var context = new WorkflowContext(workflowId, null);
        if (attributes != null) {
            context.attributes.putAll(attributes);
        }
        if (stepResults != null) {
            context.stepResults.addAll(stepResults);
        }
        return context;
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

    /**
     * 返回当前上下文属性快照。
     * <p>
     * 返回值是浅拷贝，只保证属性 Map 本身不可变；属性值对象是否可变由调用方控制。
     *
     * @return 上下文属性快照
     */
    public Map<String, Object> attributesSnapshot() {
        return Map.copyOf(attributes);
    }

    /**
     * 使用指定 reducer 更新上下文属性。
     * <p>
     * reducer 在 {@link ConcurrentMap#compute(Object, java.util.function.BiFunction)}
     * 内执行，可以把“读取当前值、合并新值、写回结果”作为一次原子更新完成。
     *
     * @param key 属性名
     * @param value 更新值
     * @param reducer 状态合并策略
     * @param <V> 属性值类型
     * @return 合并后的新值
     */
    @SuppressWarnings("unchecked")
    public <V> V updateAttribute(String key, V value, StateReducer<V> reducer) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(reducer, "reducer must not be null");
        return (V) attributes.compute(key, (ignored, current) -> reducer.reduce((V) current, value));
    }

    /**
     * 应用 step 产生的一组局部状态更新。
     *
     * @param update 状态更新对象
     */
    public void applyStateUpdate(WorkflowStateUpdate update) {
        Objects.requireNonNull(update, "update must not be null");
        for (var entry : update.entries()) {
            applyStateUpdateEntry(entry);
        }
    }

    private <V> void applyStateUpdateEntry(WorkflowStateUpdate.Entry<V> entry) {
        updateAttribute(entry.key(), entry.value(), entry.reducer());
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
