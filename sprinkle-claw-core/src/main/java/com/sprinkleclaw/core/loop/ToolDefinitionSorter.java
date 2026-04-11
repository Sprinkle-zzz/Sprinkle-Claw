package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.protocol.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 工具定义 Partition-Sort 排序器。
 * <p>将工具列表分为内置工具和外部工具两个分区，每个分区内按名称排序。
 * 确保相同工具集的输出序列稳定，提高 LLM API prompt cache 命中率。</p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public final class ToolDefinitionSorter {

    private static final Set<String> BUILTIN_TOOLS = Set.of(
            "bash", "compact", "edit_file", "glob", "grep",
            "read_file", "todo_write", "write_file",
            "task_create", "task_update", "task_list", "task_get",
            "background_run", "background_status", "background_cancel",
            "load_skill", "sub_agent"
    );

    /**
     * 对工具定义列表进行 Partition-Sort，确保相同工具集的输出序列稳定。
     *
     * @param tools 原始工具定义列表
     * @return 排序后的不可变列表
     */
    public List<ToolDefinition> sort(List<ToolDefinition> tools) {
        if (tools == null || tools.size() <= 1) {
            return tools == null ? List.of() : tools;
        }

        var builtin = new ArrayList<ToolDefinition>();
        var external = new ArrayList<ToolDefinition>();

        for (var tool : tools) {
            if (BUILTIN_TOOLS.contains(tool.name())) {
                builtin.add(tool);
            } else {
                external.add(tool);
            }
        }

        builtin.sort(Comparator.comparing(ToolDefinition::name));
        external.sort(Comparator.comparing(ToolDefinition::name));

        var result = new ArrayList<ToolDefinition>(builtin.size() + external.size());
        result.addAll(builtin);
        result.addAll(external);
        return Collections.unmodifiableList(result);
    }

    /**
     * 判断指定工具名称是否为内置工具。
     */
    public static boolean isBuiltin(String toolName) {
        return BUILTIN_TOOLS.contains(toolName);
    }
}
