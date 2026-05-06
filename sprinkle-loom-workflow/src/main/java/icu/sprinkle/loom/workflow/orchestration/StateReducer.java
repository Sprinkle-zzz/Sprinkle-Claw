package icu.sprinkle.loom.workflow.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定义 Workflow 状态字段的合并策略。
 * <p>
 * Reducer 用于把 step 产生的局部状态更新合并进 {@link WorkflowContext}。
 * 默认的 {@link #overwrite()} 适合普通标量字段；列表和 Map 类型可以使用
 * {@link #appendList()} 与 {@link #mergeMap()}，避免多个 step 直接读写同一属性时
 * 把状态合并规则散落在业务代码中。
 *
 * @param <V> 状态字段值类型
 *
 * @author sprinkle
 * @since 2026/5/6
 */
@FunctionalInterface
public interface StateReducer<V> {

    /**
     * 合并当前值和更新值。
     *
     * @param current 当前上下文中的值，第一次写入时可能为 {@code null}
     * @param update step 提交的更新值
     * @return 合并后的新值
     */
    V reduce(V current, V update);

    /**
     * 使用更新值覆盖当前值。
     *
     * @param <V> 状态字段值类型
     * @return 覆盖策略
     */
    static <V> StateReducer<V> overwrite() {
        return (current, update) -> update;
    }

    /**
     * 将更新列表追加到当前列表之后。
     *
     * @param <E> 列表元素类型
     * @return 列表追加策略
     */
    static <E> StateReducer<List<E>> appendList() {
        return (current, update) -> {
            List<E> merged = new ArrayList<>();
            if (current != null) {
                merged.addAll(current);
            }
            if (update != null) {
                merged.addAll(update);
            }
            return List.copyOf(merged);
        };
    }

    /**
     * 将更新 Map 合并到当前 Map 中；同名 key 以后提交的更新值为准。
     *
     * @param <K> Map key 类型
     * @param <V> Map value 类型
     * @return Map 合并策略
     */
    static <K, V> StateReducer<Map<K, V>> mergeMap() {
        return (current, update) -> {
            Map<K, V> merged = new LinkedHashMap<>();
            if (current != null) {
                merged.putAll(current);
            }
            if (update != null) {
                merged.putAll(update);
            }
            return Map.copyOf(merged);
        };
    }
}
