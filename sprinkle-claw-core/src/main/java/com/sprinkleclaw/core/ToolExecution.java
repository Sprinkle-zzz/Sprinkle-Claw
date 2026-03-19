package com.sprinkleclaw.core;

import java.time.Duration;

/**
 * 单次工具执行记录，用于可观测性和调试。
 *
 * @param toolCallId 工具调用 ID
 * @param toolName   工具名称
 * @param input      工具输入（序列化字符串）
 * @param output     工具输出
 * @param isError    是否执行出错
 * @param duration   执行耗时
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public record ToolExecution(
        String toolCallId,
        String toolName,
        String input,
        String output,
        boolean isError,
        Duration duration
) {
}
