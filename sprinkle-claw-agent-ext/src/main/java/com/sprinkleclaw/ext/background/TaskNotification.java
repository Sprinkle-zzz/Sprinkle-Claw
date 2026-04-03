package com.sprinkleclaw.ext.background;

import java.time.Duration;

/**
 * 后台任务完成通知，放入通知队列等待 Hook drain。
 *
 * @param taskId         任务 ID
 * @param status         最终状态
 * @param commandPreview 命令预览（最多 80 字符）
 * @param resultPreview  结果预览（最多 500 字符）
 * @param exitCode       退出码
 * @param elapsed        运行时长
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public record TaskNotification(
        String taskId,
        String status,
        String commandPreview,
        String resultPreview,
        int exitCode,
        Duration elapsed
) {}
