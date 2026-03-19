package com.sprinkleclaw.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Agent 运行配置，控制循环上限、超时、错误阈值、工作目录等。
 *
 * @param maxLoopIterations    最大循环迭代次数
 * @param loopTimeout          循环总超时时间
 * @param maxConsecutiveErrors 最大连续错误次数（超过后终止）
 * @param maxRepetitions       最大连续重复响应次数（超过后终止）
 * @param doomLoopThreshold    Doom Loop 检测阈值（同一工具+相同参数连续调用次数，默认 4）
 * @param toolOutputMaxLines   工具输出截断行数上限（默认 2000）
 * @param toolOutputMaxBytes   工具输出截断字节上限（默认 50KB）
 * @param workingDirectory     工作目录
 * @param blockedCommands      被禁止的命令列表
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public record AgentConfig(
        int maxLoopIterations,
        Duration loopTimeout,
        int maxConsecutiveErrors,
        int maxRepetitions,
        int doomLoopThreshold,
        int toolOutputMaxLines,
        int toolOutputMaxBytes,
        Path workingDirectory,
        List<String> blockedCommands
) {
    /**
     * 默认配置。
     */
    public static final AgentConfig DEFAULT = new AgentConfig(
            200, Duration.ofMinutes(30), 5, 3,
            4, 2000, 50 * 1024,
            Path.of("."), List.of()
    );

    /**
     * 创建构建器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * AgentConfig 构建器。
     */
    public static final class Builder {
        private int maxLoopIterations = 200;
        private Duration loopTimeout = Duration.ofMinutes(30);
        private int maxConsecutiveErrors = 5;
        private int maxRepetitions = 3;
        private int doomLoopThreshold = 4;
        private int toolOutputMaxLines = 2000;
        private int toolOutputMaxBytes = 50 * 1024;
        private Path workingDirectory = Path.of(".");
        private List<String> blockedCommands = List.of();

        private Builder() {
        }

        public Builder maxLoopIterations(int v) {
            this.maxLoopIterations = v;
            return this;
        }

        public Builder loopTimeout(Duration v) {
            this.loopTimeout = v;
            return this;
        }

        public Builder maxConsecutiveErrors(int v) {
            this.maxConsecutiveErrors = v;
            return this;
        }

        public Builder maxRepetitions(int v) {
            this.maxRepetitions = v;
            return this;
        }

        /**
         * 设置 Doom Loop 检测阈值（最小值 2）。
         */
        public Builder doomLoopThreshold(int v) {
            this.doomLoopThreshold = Math.max(2, v);
            return this;
        }

        /**
         * 设置工具输出截断行数上限。
         */
        public Builder toolOutputMaxLines(int v) {
            this.toolOutputMaxLines = v;
            return this;
        }

        /**
         * 设置工具输出截断字节上限。
         */
        public Builder toolOutputMaxBytes(int v) {
            this.toolOutputMaxBytes = v;
            return this;
        }

        public Builder workingDirectory(Path v) {
            this.workingDirectory = v;
            return this;
        }

        public Builder blockedCommands(List<String> v) {
            this.blockedCommands = List.copyOf(v);
            return this;
        }

        /**
         * 构建不可变的 AgentConfig 实例。
         */
        public AgentConfig build() {
            return new AgentConfig(maxLoopIterations, loopTimeout, maxConsecutiveErrors,
                    maxRepetitions, doomLoopThreshold, toolOutputMaxLines, toolOutputMaxBytes,
                    workingDirectory, blockedCommands);
        }
    }
}
