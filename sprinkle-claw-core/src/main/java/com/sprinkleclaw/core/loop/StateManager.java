package com.sprinkleclaw.core.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 组件状态管理器。
 * <p>负责注册有状态组件、收集状态快照、从持久化数据恢复组件状态。
 * 在会话持久化的 flush 节点由框架调用。</p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);

    private final List<StatePersistable> components = new CopyOnWriteArrayList<>();

    /**
     * 注册有状态组件。
     *
     * @param component 实现 {@link StatePersistable} 的组件
     * @throws IllegalArgumentException 如果 stateId 重复
     */
    public void register(StatePersistable component) {
        boolean duplicate = components.stream()
                .anyMatch(c -> c.stateId().equals(component.stateId()));
        if (duplicate) {
            throw new IllegalArgumentException("Duplicate stateId: " + component.stateId());
        }
        components.add(component);
        log.debug("Registered state component: {}", component.stateId());
    }

    /**
     * 收集所有脏组件的状态。
     *
     * @return stateId → 状态 Map 的映射
     */
    public Map<String, Map<String, Object>> collectStates() {
        var states = new LinkedHashMap<String, Map<String, Object>>();
        for (var component : components) {
            if (component.isDirty()) {
                try {
                    states.put(component.stateId(), component.saveState());
                } catch (Exception e) {
                    log.warn("Failed to collect state for component: {}", component.stateId(), e);
                }
            }
        }
        return states;
    }

    /**
     * 从持久化数据恢复所有组件状态。
     *
     * @param states stateId → 状态 Map 的映射
     */
    public void restoreAll(Map<String, Map<String, Object>> states) {
        if (states == null) return;
        for (var component : components) {
            var state = states.getOrDefault(component.stateId(), Map.of());
            try {
                component.restoreState(state);
                log.debug("Restored state for component: {}", component.stateId());
            } catch (Exception e) {
                log.warn("Failed to restore state for component: {}", component.stateId(), e);
            }
        }
    }

    /**
     * 获取已注册组件数。
     */
    public int componentCount() {
        return components.size();
    }

    /**
     * 获取所有已注册的 stateId。
     */
    public List<String> registeredIds() {
        return components.stream().map(StatePersistable::stateId).toList();
    }
}
