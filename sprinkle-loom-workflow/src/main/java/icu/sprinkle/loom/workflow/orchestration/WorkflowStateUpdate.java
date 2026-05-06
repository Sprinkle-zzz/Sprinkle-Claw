package icu.sprinkle.loom.workflow.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Workflow step 产生的一组局部状态更新。
 * <p>
 * 该类型只描述“要更新哪些上下文字段以及如何合并”，不承载 step 的业务输出。
 * 调用方可通过 {@link WorkflowContext#applyStateUpdate(WorkflowStateUpdate)}
 * 将其应用到上下文中。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
public final class WorkflowStateUpdate {

    private final List<Entry<?>> entries;

    private WorkflowStateUpdate(List<Entry<?>> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * 创建空的状态更新构建器。
     *
     * @return 状态更新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    List<Entry<?>> entries() {
        return entries;
    }

    record Entry<V>(String key, V value, StateReducer<V> reducer) {
    }

    /**
     * {@link WorkflowStateUpdate} 构建器。
     */
    public static final class Builder {
        private final List<Entry<?>> entries = new ArrayList<>();

        private Builder() {
        }

        /**
         * 添加一个使用覆盖策略的字段更新。
         *
         * @param key 状态字段名
         * @param value 更新值
         * @return 当前构建器
         */
        public Builder put(String key, Object value) {
            return put(key, value, StateReducer.overwrite());
        }

        /**
         * 添加一个使用指定 reducer 的字段更新。
         *
         * @param key 状态字段名
         * @param value 更新值
         * @param reducer 状态合并策略
         * @param <V> 状态字段值类型
         * @return 当前构建器
         */
        public <V> Builder put(String key, V value, StateReducer<V> reducer) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(reducer, "reducer must not be null");
            entries.add(new Entry<>(key, value, reducer));
            return this;
        }

        /**
         * 构建不可变状态更新对象。
         *
         * @return 状态更新对象
         */
        public WorkflowStateUpdate build() {
            return new WorkflowStateUpdate(entries);
        }
    }
}
