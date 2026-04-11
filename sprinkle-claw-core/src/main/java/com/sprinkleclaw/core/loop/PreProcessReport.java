package com.sprinkleclaw.core.loop;

import java.time.Duration;
import java.util.Optional;

/**
 * 预处理管线执行报告。
 * <p>记录每个阶段的执行结果，用于 {@link LoopTrace} 和调试诊断。</p>
 *
 * @param iteration            迭代轮次
 * @param toolOutputsTruncated 阶段 1 截断的工具输出数
 * @param microResult          阶段 2 微压缩结果
 * @param pruneResult          阶段 3 裁剪压缩结果（未触发时 empty）
 * @param autoResult           阶段 4 自动压缩结果（未触发时 empty）
 * @param notificationsDrained 阶段 5 注入的通知数
 * @param totalDuration        整个管线耗时
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public record PreProcessReport(
        int iteration,
        int toolOutputsTruncated,
        MicroCompactResult microResult,
        Optional<CompactResult> pruneResult,
        Optional<CompactResult> autoResult,
        int notificationsDrained,
        Duration totalDuration
) {

    /**
     * 微压缩结果。
     */
    public record MicroCompactResult(
            int placeholdersReplaced,
            int errorsRemoved,
            int tokensSaved
    ) {
        public static final MicroCompactResult NONE = new MicroCompactResult(0, 0, 0);
    }

    /**
     * 压缩结果（裁剪/自动）。
     */
    public record CompactResult(
            int beforeTokens,
            int afterTokens,
            double compressionRatio
    ) {
    }

    /**
     * 是否有任何实质性操作。
     */
    public boolean hadWork() {
        return toolOutputsTruncated > 0
                || (microResult != null && microResult.tokensSaved() > 0)
                || pruneResult.isPresent()
                || autoResult.isPresent()
                || notificationsDrained > 0;
    }

    /**
     * 所有阶段节省的总 token 数。
     */
    public int totalTokensSaved() {
        int saved = microResult != null ? microResult.tokensSaved() : 0;
        saved += pruneResult.map(r -> r.beforeTokens() - r.afterTokens()).orElse(0);
        saved += autoResult.map(r -> r.beforeTokens() - r.afterTokens()).orElse(0);
        return saved;
    }

    /**
     * Builder 用于逐步构建报告。
     */
    public static class Builder {
        private final int iteration;
        private int toolOutputsTruncated;
        private MicroCompactResult microResult = MicroCompactResult.NONE;
        private CompactResult pruneResult;
        private CompactResult autoResult;
        private int notificationsDrained;
        private final long startNanos;

        public Builder(int iteration) {
            this.iteration = iteration;
            this.startNanos = System.nanoTime();
        }

        public Builder toolResultBudget(int truncatedCount) {
            this.toolOutputsTruncated = truncatedCount;
            return this;
        }

        public Builder microCompact(MicroCompactResult result) {
            this.microResult = result;
            return this;
        }

        public Builder pruneCompact(CompactResult result) {
            this.pruneResult = result;
            return this;
        }

        public Builder autoCompact(CompactResult result) {
            this.autoResult = result;
            return this;
        }

        public Builder notificationsDrained(int count) {
            this.notificationsDrained = count;
            return this;
        }

        public PreProcessReport build() {
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            return new PreProcessReport(
                    iteration,
                    toolOutputsTruncated,
                    microResult,
                    Optional.ofNullable(pruneResult),
                    Optional.ofNullable(autoResult),
                    notificationsDrained,
                    duration
            );
        }
    }
}
