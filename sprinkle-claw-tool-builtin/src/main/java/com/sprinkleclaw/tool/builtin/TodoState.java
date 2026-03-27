package com.sprinkleclaw.tool.builtin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办事项列表状态，存储在 {@code AgentContext.attributes} / {@code ToolContext.attributes} 中。
 *
 * <p>通过 {@link #CONTEXT_KEY} 在上下文属性映射中存取。
 * 支持合并模式（按 id 更新已有项）和替换模式（全量替换）。</p>
 *
 * @param items         待办事项列表
 * @param lastUpdatedAt 最后更新时间
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public record TodoState(
        List<TodoItem> items,
        Instant lastUpdatedAt
) {

    /**
     * 在 AgentContext.attributes 和 ToolContext.attributes 中的存储键。
     */
    public static final String CONTEXT_KEY = "todo_state";

    public TodoState {
        items = List.copyOf(items);
    }

    /**
     * 创建空状态。
     */
    public static TodoState empty() {
        return new TodoState(List.of(), Instant.now());
    }

    /**
     * 合并模式：按 id 匹配更新已有项，新增不存在的项。
     *
     * @param updates 要合并的待办项
     * @return 合并后的新状态
     */
    public TodoState merge(List<TodoItem> updates) {
        Map<String, TodoItem> merged = new LinkedHashMap<>();
        for (TodoItem item : items) {
            merged.put(item.id(), item);
        }
        for (TodoItem update : updates) {
            merged.put(update.id(), update);
        }
        return new TodoState(new ArrayList<>(merged.values()), Instant.now());
    }

    /**
     * 替换模式：用新列表完全替换。
     *
     * @param newItems 新的待办项列表
     * @return 替换后的新状态
     */
    public TodoState replace(List<TodoItem> newItems) {
        return new TodoState(new ArrayList<>(newItems), Instant.now());
    }

    /**
     * 格式化为可读文本（用于注入 system prompt 或回复模型）。
     *
     * @return 格式化的待办列表文本
     */
    public String formatAsText() {
        if (items.isEmpty()) {
            return "(No active todos)";
        }

        var sb = new StringBuilder();
        for (TodoItem item : items) {
            String icon = switch (item.status()) {
                case "completed" -> "[x]";
                case "in_progress" -> "[>]";
                case "cancelled" -> "[-]";
                default -> "[ ]";
            };
            sb.append(icon).append(' ').append(item.id())
                    .append(": ").append(item.content())
                    .append(" (").append(item.status()).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 获取精简版状态（仅保留活跃项），用于压缩后注入。
     * 过滤掉 completed 和 cancelled 状态的项；
     * 如果剩余超过 30 项，仅保留 in_progress 和最近 20 个 pending。
     *
     * @return 精简后的新状态
     */
    public TodoState compact() {
        List<TodoItem> active = items.stream()
                .filter(i -> !"completed".equals(i.status()) && !"cancelled".equals(i.status()))
                .toList();

        if (active.size() <= 30) {
            return new TodoState(new ArrayList<>(active), lastUpdatedAt);
        }

        List<TodoItem> result = new ArrayList<>();
        List<TodoItem> pending = new ArrayList<>();
        for (TodoItem item : active) {
            if ("in_progress".equals(item.status())) {
                result.add(item);
            } else {
                pending.add(item);
            }
        }
        int limit = Math.min(20, pending.size());
        result.addAll(pending.subList(pending.size() - limit, pending.size()));
        return new TodoState(result, lastUpdatedAt);
    }
}
