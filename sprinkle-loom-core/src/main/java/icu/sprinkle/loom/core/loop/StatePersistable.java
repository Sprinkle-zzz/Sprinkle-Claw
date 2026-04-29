package icu.sprinkle.loom.core.loop;

import java.util.Map;

/**
 * 泛化状态持久化 SPI。
 * <p>任何有状态的组件可实现此接口，实现自治的状态持久化。
 * {@link StateManager} 在 flush 节点统一调用所有已注册组件的 {@link #saveState()}。</p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public interface StatePersistable {

    /**
     * 组件状态标识，全局唯一。
     * <p>用于在持久化存储中区分不同组件的状态。
     * 例如："task-board", "background-manager", "skill-cache"</p>
     */
    String stateId();

    /**
     * 导出当前状态为可序列化的 Map。
     * <p>只包含恢复所需的最小数据。</p>
     */
    Map<String, Object> saveState();

    /**
     * 从保存的状态恢复。
     *
     * @param state 之前 {@link #saveState()} 返回的 Map（可能为 null 或空 Map）
     */
    void restoreState(Map<String, Object> state);

    /**
     * 状态是否有变化（脏标记）。
     * <p>仅在 dirty 时才序列化，减少 I/O。
     * 默认 true（保守策略：总是保存）。</p>
     */
    default boolean isDirty() {
        return true;
    }
}
