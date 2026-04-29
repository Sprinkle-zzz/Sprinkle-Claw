package icu.sprinkle.loom.ext.background;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.loop.LoopHook;

import java.util.List;

/**
 * 后台任务通知 Hook：在每轮 LLM 调用前 drain 通知队列，
 * 将已完成的后台任务结果注入上下文，使 LLM 知晓。
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class BackgroundNotificationHook implements LoopHook {

    private final BackgroundManager bgManager;

    public BackgroundNotificationHook(BackgroundManager bgManager) {
        this.bgManager = bgManager;
    }

    @Override
    public void preLlmCall(AgentContext context, int iteration) {
        List<TaskNotification> notifications = bgManager.drainNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        var sb = new StringBuilder();
        sb.append("<background-results>\n");
        for (TaskNotification n : notifications) {
            sb.append("[bg:").append(n.taskId()).append("] ");
            sb.append(n.status());
            sb.append(" (exit=").append(n.exitCode());
            sb.append(", ").append(n.elapsed().toSeconds()).append("s): ");
            sb.append(n.commandPreview()).append("\n");
            if (n.resultPreview() != null && !n.resultPreview().isBlank()) {
                sb.append("  Output: ").append(n.resultPreview()).append("\n");
            }
        }
        sb.append("</background-results>");

        // 注入通知消息，LLM 在下次调用时可以看到
        context.injectNotification(sb.toString());
    }
}
