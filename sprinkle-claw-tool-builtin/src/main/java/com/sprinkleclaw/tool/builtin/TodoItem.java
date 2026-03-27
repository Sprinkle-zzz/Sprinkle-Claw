package com.sprinkleclaw.tool.builtin;

/**
 * 单个待办事项。
 *
 * @param id      唯一标识符
 * @param content 任务内容描述
 * @param status  任务状态（pending / in_progress / completed / cancelled）
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public record TodoItem(
        String id,
        String content,
        String status
) {
}
