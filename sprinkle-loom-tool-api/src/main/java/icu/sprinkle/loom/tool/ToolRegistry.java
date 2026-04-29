package icu.sprinkle.loom.tool;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的工具注册表，管理所有已注册的 {@link AgentTool}。
 * <p>使用 {@link ConcurrentHashMap} 存储，支持并发读写。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * 注册一个工具。若已存在同名工具则覆盖。
     *
     * @param tool 要注册的工具
     */
    public void register(AgentTool tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        tools.put(tool.name(), tool);
    }

    /**
     * 批量注册工具。
     *
     * @param tools 要注册的工具集合
     */
    public void registerAll(Collection<? extends AgentTool> tools) {
        tools.forEach(this::register);
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名称
     * @return 工具实例（可能为空）
     */
    public Optional<AgentTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 获取所有已注册工具的定义列表（用于发送给 LLM）。
     *
     * @return 工具定义列表
     */
    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(AgentTool::definition)
                .toList();
    }

    /**
     * 获取所有已注册工具的不可变视图。
     *
     * @return 工具集合
     */
    public Collection<AgentTool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 获取已注册工具数量。
     *
     * @return 工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 创建副本，排除指定名称的工具（用于 SubAgent 工具过滤）。
     *
     * @param excludedNames 要排除的工具名称集合
     * @return 新的 ToolRegistry（不包含被排除的工具）
     */
    public ToolRegistry copyWithout(Set<String> excludedNames) {
        ToolRegistry copy = new ToolRegistry();
        tools.forEach((name, tool) -> {
            if (!excludedNames.contains(name)) {
                copy.register(tool);
            }
        });
        return copy;
    }

    /**
     * 创建副本，仅保留指定名称的工具（用于 SubAgent 白名单过滤）。
     *
     * @param allowedNames 允许的工具名称列表
     * @return 新的 ToolRegistry（仅包含允许的工具）
     */
    public ToolRegistry copyWithOnly(List<String> allowedNames) {
        Set<String> allowed = new java.util.HashSet<>(allowedNames);
        ToolRegistry copy = new ToolRegistry();
        tools.forEach((name, tool) -> {
            if (allowed.contains(name)) {
                copy.register(tool);
            }
        });
        return copy;
    }
}
