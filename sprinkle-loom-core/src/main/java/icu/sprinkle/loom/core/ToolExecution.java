package icu.sprinkle.loom.core;

import java.time.Duration;
import java.nio.charset.StandardCharsets;

/**
 * 单次工具执行记录，用于可观测性和调试。
 *
 * @param toolCallId 工具调用 ID
 * @param toolName   工具名称
 * @param input      工具输入（序列化字符串）
 * @param output     工具输出
 * @param isError    是否执行出错
 * @param duration   执行耗时
 * @param truncated  输出是否被截断或外部化
 * @param originalBytes 原始输出字节数
 * @param emittedBytes  实际输出字节数
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
        Duration duration,
        boolean truncated,
        int originalBytes,
        int emittedBytes
) {
    public ToolExecution(String toolCallId, String toolName, String input, String output,
                         boolean isError, Duration duration) {
        this(toolCallId, toolName, input, output, isError, duration,
                false, bytes(output), bytes(output));
    }

    private static int bytes(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }
}
