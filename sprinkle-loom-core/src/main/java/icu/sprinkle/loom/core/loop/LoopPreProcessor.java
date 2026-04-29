package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.context.CompactionResult;
import icu.sprinkle.loom.core.context.MicroCompactor;
import icu.sprinkle.loom.core.context.PruneCompactor;
import icu.sprinkle.loom.core.context.AutoCompactor;
import icu.sprinkle.loom.core.context.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 统一预处理管线。
 * <p>每轮 LLM 调用前按固定顺序执行 5 个阶段：
 * <ol>
 *   <li>ToolResultBudget — 工具输出预算检查与截断</li>
 *   <li>MicroCompact — 占位符替换、错误输出裁剪</li>
 *   <li>PruneCompact — 动态阈值裁剪（超阈值时触发）</li>
 *   <li>AutoCompact — LLM 结构化摘要（超阈值时触发）</li>
 *   <li>NotificationDrain — 后台任务通知 + 任务提醒注入</li>
 * </ol></p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public final class LoopPreProcessor {

    private static final Logger log = LoggerFactory.getLogger(LoopPreProcessor.class);

    private final MicroCompactor microCompactor;
    private final PruneCompactor pruneCompactor;
    private final AutoCompactor autoCompactor;
    private final TokenEstimator tokenEstimator;
    private final int compactionThreshold;

    public LoopPreProcessor(MicroCompactor microCompactor,
                            PruneCompactor pruneCompactor,
                            AutoCompactor autoCompactor,
                            TokenEstimator tokenEstimator,
                            int compactionThreshold) {
        this.microCompactor = microCompactor;
        this.pruneCompactor = pruneCompactor;
        this.autoCompactor = autoCompactor;
        this.tokenEstimator = tokenEstimator;
        this.compactionThreshold = compactionThreshold;
    }

    /**
     * 执行完整的预处理管线。
     *
     * @param ctx       Agent 上下文
     * @param iteration 当前迭代轮次
     * @return 预处理报告
     */
    public PreProcessReport process(AgentContext ctx, int iteration) {
        var report = new PreProcessReport.Builder(iteration);

        // 阶段 1：工具输出预算（截断由 ToolExecutor 在执行时完成，此处记录状态）
        report.toolResultBudget(0);

        // 阶段 2：微压缩（始终执行，低开销）
        if (microCompactor != null) {
            CompactionResult microResult = microCompactor.compact(ctx);
            if (microResult != null) {
                report.microCompact(new PreProcessReport.MicroCompactResult(
                        microResult.messagesRemoved(), 0, microResult.tokensSaved()));
            }
        }

        // 估算当前 token 数
        int estimatedTokens = tokenEstimator != null
                ? tokenEstimator.estimate(ctx.mutableMessages()) : 0;

        // 阶段 3：裁剪压缩（token > threshold × 0.8 时触发）
        if (pruneCompactor != null && compactionThreshold > 0
                && estimatedTokens > compactionThreshold * 0.8) {
            CompactionResult pruneResult = pruneCompactor.compact(ctx);
            if (pruneResult != null) {
                report.pruneCompact(new PreProcessReport.CompactResult(
                        pruneResult.tokensBefore(), pruneResult.tokensAfter(),
                        pruneResult.tokensBefore() > 0
                                ? 1.0 - ((double) pruneResult.tokensAfter() / pruneResult.tokensBefore())
                                : 0.0));
                // 重新估算
                if (tokenEstimator != null) {
                    estimatedTokens = tokenEstimator.estimate(ctx.mutableMessages());
                }
            }
        }

        // 阶段 4：自动压缩（仍超阈值时触发）
        if (autoCompactor != null && compactionThreshold > 0
                && estimatedTokens > compactionThreshold) {
            CompactionResult autoResult = autoCompactor.compact(ctx);
            if (autoResult != null) {
                report.autoCompact(new PreProcessReport.CompactResult(
                        autoResult.tokensBefore(), autoResult.tokensAfter(),
                        autoResult.tokensBefore() > 0
                                ? 1.0 - ((double) autoResult.tokensAfter() / autoResult.tokensBefore())
                                : 0.0));
            }
        }

        // 阶段 5：通知注入
        int notifications = drainNotifications(ctx);
        report.notificationsDrained(notifications);

        PreProcessReport result = report.build();
        if (result.hadWork()) {
            log.debug("[LoopPreProcessor] iteration={}, truncated={}, microSaved={}, prune={}, auto={}, notif={}",
                    iteration, result.toolOutputsTruncated(),
                    result.microResult().tokensSaved(),
                    result.pruneResult().isPresent(), result.autoResult().isPresent(),
                    result.notificationsDrained());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private int drainNotifications(AgentContext ctx) {
        List<String> notifications = ctx.getAttribute("_pending_notifications");
        if (notifications == null || notifications.isEmpty()) {
            return 0;
        }
        int count = notifications.size();
        for (String notification : notifications) {
            ctx.appendUserMessage("[system] " + notification);
        }
        ctx.setAttribute("_pending_notifications", null);
        return count;
    }
}
