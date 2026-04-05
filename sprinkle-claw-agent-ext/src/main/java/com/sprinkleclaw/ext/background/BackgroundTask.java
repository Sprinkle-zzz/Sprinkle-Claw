package com.sprinkleclaw.ext.background;

import java.time.Duration;
import java.time.Instant;

/**
 * 后台任务状态记录。
 *
 * @param id        任务 ID（8 位随机十六进制）
 * @param command   执行的命令
 * @param status    状态：running | completed | timeout | error | cancelled
 * @param result    执行结果（completed 时为输出，error 时为错误信息）
 * @param exitCode  进程退出码（running 时为 null）
 * @param startedAt 启动时间
 * @param endedAt   结束时间（running 时为 null）
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public record BackgroundTask(
        String id,
        String command,
        String status,
        String result,
        Integer exitCode,
        Instant startedAt,
        Instant endedAt
) {
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_CANCELLED = "cancelled";

    /**
     * 是否仍在运行。
     */
    public boolean isRunning() {
        return STATUS_RUNNING.equals(status);
    }

    /**
     * 已运行时长。
     */
    public Duration elapsed() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    /**
     * 格式化为摘要文本（用于通知和查询）。
     *
     * @param maxResultLength 结果预览最大长度
     */
    public String formatSummary(int maxResultLength) {
        var sb = new StringBuilder();
        sb.append("[bg:").append(id).append("] ").append(status);
        sb.append(" (").append(elapsed().toSeconds()).append("s)");
        String cmdPreview = command.length() > 80 ? command.substring(0, 80) + "..." : command;
        sb.append(": ").append(cmdPreview);
        if (exitCode != null) {
            sb.append(" [exit=").append(exitCode).append("]");
        }
        if (result != null && !result.isBlank()) {
            String preview = result.length() > maxResultLength
                    ? result.substring(0, maxResultLength) + "..."
                    : result;
            sb.append("\n  Output: ").append(preview);
        }
        return sb.toString();
    }
}
