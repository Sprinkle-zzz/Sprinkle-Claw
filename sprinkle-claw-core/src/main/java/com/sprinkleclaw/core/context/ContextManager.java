package com.sprinkleclaw.core.context;

import com.sprinkleclaw.core.loop.LoopHook;
import com.sprinkleclaw.protocol.llm.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 上下文压缩调度中枢，协调三层压缩器的执行。
 *
 * <h3>调度策略</h3>
 * <ol>
 *   <li><b>每轮</b>：执行 {@link MicroCompactor}（旧工具结果 → 占位符）</li>
 *   <li><b>token &gt; threshold × 0.8</b>：执行 {@link PruneCompactor}（激进裁剪）</li>
 *   <li><b>token 仍 &gt; threshold</b>：执行 {@link AutoCompactor}（LLM 摘要）</li>
 * </ol>
 *
 * <h3>双轨溢出判断（v1.1）</h3>
 * <ul>
 *   <li>MicroCompactor / PruneCompactor：使用 {@link TokenEstimator} 估算值</li>
 *   <li>AutoCompactor 触发判断：优先使用上一轮 LLM 调用返回的 API usage 精确值，
 *       不可用时回退到估算值</li>
 * </ul>
 *
 * <h3>可选性</h3>
 * <p>此组件在 MVP2 中是可选的。如果用户不配置 compactionThreshold，
 * {@code ClawBuilder} 不创建 ContextManager，AgentLoop 中跳过压缩调度。</p>
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public final class ContextManager {

    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);

    /**
     * AutoCompactor 触发缓冲区：预留给模型输出的 token 空间
     */
    private static final int COMPACTION_BUFFER = 4000;

    /**
     * 连续压缩次数警告阈值
     */
    private static final int CONSECUTIVE_COMPACTION_WARN = 3;

    private final TokenEstimator tokenEstimator;
    private final MicroCompactor microCompactor;
    private final PruneCompactor pruneCompactor;
    private final AutoCompactor autoCompactor;
    private final int compactionThreshold;
    private final List<LoopHook> hooks;

    /**
     * 创建上下文压缩调度器。
     *
     * @param tokenEstimator      token 估算器
     * @param microCompactor      微压缩器
     * @param pruneCompactor      裁剪压缩器
     * @param autoCompactor       自动压缩器
     * @param compactionThreshold 触发自动压缩的 token 阈值
     * @param hooks               生命周期钩子列表（压缩后触发 afterCompaction）
     */
    public ContextManager(TokenEstimator tokenEstimator,
                          MicroCompactor microCompactor,
                          PruneCompactor pruneCompactor,
                          AutoCompactor autoCompactor,
                          int compactionThreshold,
                          List<LoopHook> hooks) {
        this.tokenEstimator = tokenEstimator;
        this.microCompactor = microCompactor;
        this.pruneCompactor = pruneCompactor;
        this.autoCompactor = autoCompactor;
        this.compactionThreshold = compactionThreshold;
        this.hooks = hooks != null ? List.copyOf(hooks) : List.of();
    }

    /**
     * 每轮 LLM 调用前由 AgentLoop 调用，按需执行压缩。
     *
     * @param context Agent 上下文
     */
    public void compactIfNeeded(AgentContext context) {
        // Layer 1: MicroCompactor（每轮执行）
        CompactionResult microResult = microCompactor.compact(context);
        if (microResult != null) {
            notifyHooks(context, microResult);
        }

        // 估算当前 token 数并缓存
        int estimatedTokens = tokenEstimator.estimate(context.mutableMessages());
        context.setCachedTokenCount(estimatedTokens);

        // Layer 1.5: PruneCompactor（token > threshold × 0.8 时触发）
        if (estimatedTokens > compactionThreshold * 0.8) {
            CompactionResult pruneResult = pruneCompactor.compact(context);
            if (pruneResult != null) {
                notifyHooks(context, pruneResult);
                estimatedTokens = tokenEstimator.estimate(context.mutableMessages());
                context.setCachedTokenCount(estimatedTokens);
            }
        }

        // Layer 2: AutoCompactor（优先使用 API usage 精确判断）
        if (shouldAutoCompact(context, estimatedTokens)) {
            CompactionResult autoResult = autoCompactor.compact(context);
            if (autoResult != null) {
                notifyHooks(context, autoResult);
                context.setCachedTokenCount(
                        tokenEstimator.estimate(context.mutableMessages()));
            }
        }

        // 连续压缩警告
        if (context.compactionCount() > 0
                && context.compactionCount() % CONSECUTIVE_COMPACTION_WARN == 0) {
            log.warn("[ContextManager] 连续 {} 次压缩，请考虑增大 compactionThreshold（当前 {}）",
                    context.compactionCount(), compactionThreshold);
        }
    }

    /**
     * 手动触发压缩（由 compact 工具调用），跳过 MicroCompactor。
     *
     * @param context Agent 上下文
     * @param focus   可选的压缩焦点提示（当前版本忽略，留作 MVP3 扩展）
     * @return 压缩结果
     */
    public CompactionResult manualCompact(AgentContext context, String focus) {
        // 先尝试 Prune
        CompactionResult pruneResult = pruneCompactor.compact(context);
        if (pruneResult != null) {
            notifyHooks(context, pruneResult);
        }

        // 再执行 Auto
        CompactionResult autoResult = autoCompactor.compact(context);
        if (autoResult != null) {
            notifyHooks(context, autoResult);
            return autoResult;
        }

        return pruneResult;
    }

    /**
     * 获取当前 token 估算值（优先返回缓存值）。
     *
     * @param context Agent 上下文
     * @return 当前消息列表的 token 估算值
     */
    public int getCurrentTokenCount(AgentContext context) {
        int cached = context.cachedTokenCount();
        if (cached >= 0) {
            return cached;
        }
        int estimated = tokenEstimator.estimate(context.mutableMessages());
        context.setCachedTokenCount(estimated);
        return estimated;
    }

    /**
     * 判断是否应触发 AutoCompactor。
     *
     * <p>双轨策略：优先使用上一轮 LLM 调用返回的 API usage 精确值，
     * 不可用时回退到估算值。</p>
     */
    private boolean shouldAutoCompact(AgentContext context, int estimatedTokens) {
        Usage lastUsage = context.lastTokenUsage();
        if (lastUsage != null && lastUsage.inputTokens() > 0) {
            // 精确判断：实际输入 token >= 可用窗口 - 缓冲区
            int modelWindow = context.modelContextWindow();
            int maxOutput = context.modelMaxOutputTokens();
            if (modelWindow > 0 && maxOutput > 0) {
                int usableWindow = modelWindow - maxOutput;
                return lastUsage.inputTokens() >= usableWindow - COMPACTION_BUFFER;
            }
        }
        // 回退到估算值
        return estimatedTokens > compactionThreshold;
    }

    /**
     * 通知所有 Hook 压缩已完成。
     */
    private void notifyHooks(AgentContext context, CompactionResult result) {
        for (LoopHook hook : hooks) {
            try {
                hook.afterCompaction(context, result);
            } catch (Exception e) {
                log.warn("[ContextManager] afterCompaction hook 执行异常", e);
            }
        }
    }
}
