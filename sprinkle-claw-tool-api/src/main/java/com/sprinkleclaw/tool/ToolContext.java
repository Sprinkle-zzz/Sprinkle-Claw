package com.sprinkleclaw.tool;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文，传递给每次工具调用的环境信息。
 *
 * <p>MVP2 新增 {@code attributes} 通用属性映射，用于在 AgentLoop 与工具之间
 * 传递扩展数据（如 TodoState、FileTimestampCache 等）。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class ToolContext {

    private final Path workingDirectory;
    private final Map<String, Object> attributes;

    /**
     * 创建包含属性映射的工具上下文。
     *
     * @param workingDirectory 工作目录
     * @param attributes       共享属性映射（引用 AgentContext.attributes）
     */
    public ToolContext(Path workingDirectory, Map<String, Object> attributes) {
        this.workingDirectory = workingDirectory;
        this.attributes = attributes != null ? attributes : new ConcurrentHashMap<>();
    }

    /**
     * 创建工具上下文（兼容 MVP1，无属性映射）。
     *
     * @param workingDirectory 工作目录
     */
    public ToolContext(Path workingDirectory) {
        this(workingDirectory, new ConcurrentHashMap<>());
    }

    /**
     * 获取工作目录。
     */
    public Path workingDirectory() {
        return workingDirectory;
    }

    /**
     * 获取属性值。
     *
     * @param key  属性键
     * @param type 值类型
     * @param <T>  值类型
     * @return 属性值，不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * 获取属性值（无类型检查）。
     *
     * @param key 属性键
     * @param <T> 值类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 设置属性值。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }
}
