package com.sprinkleclaw.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Agent 运行配置，控制循环上限、超时、错误阈值、压缩策略、会话管理等。
 *
 * @param maxLoopIterations           最大循环迭代次数
 * @param loopTimeout                 循环总超时时间
 * @param maxConsecutiveErrors        最大连续错误次数（超过后终止）
 * @param maxRepetitions              最大连续重复响应次数（超过后终止）
 * @param doomLoopThreshold           Doom Loop 检测阈值（同一工具+相同参数连续调用次数，默认 4）
 * @param toolOutputMaxLines          工具输出截断行数上限（默认 2000）
 * @param toolOutputMaxBytes          工具输出截断字节上限（默认 50KB）
 * @param workingDirectory            工作目录
 * @param blockedCommands             被禁止的命令列表
 * @param compactionThreshold         触发自动压缩的 token 阈值（默认 100K）
 * @param microCompactKeepRecent      MicroCompactor 保留最近 N 个 tool_result 不替换
 * @param pruneProtectTokens          Prune 保护区大小（最近 N tokens 不裁剪，0 表示使用动态计算）
 * @param pruneMinimumTokens          Prune 最小裁剪量（低于此值不执行裁剪，0 表示使用动态计算）
 * @param persistSessions             是否启用会话持久化
 * @param sessionDirectory            会话存储目录
 * @param autoSaveInterval            每 N 轮自动保存一次（0 禁用）
 * @param enableTodoWrite             是否启用 TodoWrite 工具
 * @param todoNagThreshold            TodoWrite 提醒间隔（多轮未更新后注入提醒）
 * @param enableFileSnapshot          是否启用文件快照追踪
 * @param toolOutputDynamicTruncation 工具输出截断阈值是否随上下文使用率动态调整
 * @param modelContextWindow          模型上下文窗口大小（0 表示自动从模型元数据获取）
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
        List<String> blockedCommands,
        int compactionThreshold,
        int microCompactKeepRecent,
        int pruneProtectTokens,
        int pruneMinimumTokens,
        boolean persistSessions,
        Path sessionDirectory,
        int autoSaveInterval,
        boolean enableTodoWrite,
        int todoNagThreshold,
        boolean enableFileSnapshot,
        boolean toolOutputDynamicTruncation,
        int modelContextWindow
) {
    /**
     * 默认配置。
     */
    public static final AgentConfig DEFAULT = new AgentConfig(
            200, Duration.ofMinutes(30), 5, 3,
            4, 2000, 50 * 1024,
            Path.of("."), List.of(),
            100_000, 3, 0, 0,
            false, Path.of(".sessions"), 5,
            false, 3, false, true, 0
    );

    /**
     * 创建构建器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * AgentConfig 构建器，Fluent API 风格。
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
        private int compactionThreshold = 100_000;
        private int microCompactKeepRecent = 3;
        private int pruneProtectTokens = 0;
        private int pruneMinimumTokens = 0;
        private boolean persistSessions = false;
        private Path sessionDirectory = Path.of(".sessions");
        private int autoSaveInterval = 5;
        private boolean enableTodoWrite = false;
        private int todoNagThreshold = 3;
        private boolean enableFileSnapshot = false;
        private boolean toolOutputDynamicTruncation = true;
        private int modelContextWindow = 0;

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
         * 设置触发自动压缩的 token 阈值。
         */
        public Builder compactionThreshold(int v) {
            this.compactionThreshold = v;
            return this;
        }

        /**
         * 设置 MicroCompactor 保留最近 N 个工具结果不替换。
         */
        public Builder microCompactKeepRecent(int v) {
            this.microCompactKeepRecent = Math.max(1, v);
            return this;
        }

        /**
         * 设置 Prune 保护区大小（0 表示按模型窗口动态计算）。
         */
        public Builder pruneProtectTokens(int v) {
            this.pruneProtectTokens = v;
            return this;
        }

        /**
         * 设置 Prune 最小裁剪量（0 表示按模型窗口动态计算）。
         */
        public Builder pruneMinimumTokens(int v) {
            this.pruneMinimumTokens = v;
            return this;
        }

        /**
         * 设置是否启用会话持久化。
         */
        public Builder persistSessions(boolean v) {
            this.persistSessions = v;
            return this;
        }

        /**
         * 设置会话存储目录。
         */
        public Builder sessionDirectory(Path v) {
            this.sessionDirectory = v;
            return this;
        }

        /**
         * 设置自动保存间隔（每 N 轮保存一次，0 禁用）。
         */
        public Builder autoSaveInterval(int v) {
            this.autoSaveInterval = v;
            return this;
        }

        /**
         * 设置是否启用 TodoWrite 工具。
         */
        public Builder enableTodoWrite(boolean v) {
            this.enableTodoWrite = v;
            return this;
        }

        /**
         * 设置 TodoWrite 提醒间隔。
         */
        public Builder todoNagThreshold(int v) {
            this.todoNagThreshold = Math.max(1, v);
            return this;
        }

        /**
         * 设置是否启用文件快照追踪。
         */
        public Builder enableFileSnapshot(boolean v) {
            this.enableFileSnapshot = v;
            return this;
        }

        /**
         * 设置是否启用工具输出动态截断（根据上下文使用率调整截断阈值）。
         */
        public Builder toolOutputDynamicTruncation(boolean v) {
            this.toolOutputDynamicTruncation = v;
            return this;
        }

        /**
         * 设置模型上下文窗口大小（0 表示自动从模型元数据获取）。
         */
        public Builder modelContextWindow(int v) {
            this.modelContextWindow = v;
            return this;
        }

        /**
         * 构建不可变的 AgentConfig 实例。
         */
        public AgentConfig build() {
            return new AgentConfig(
                    maxLoopIterations, loopTimeout, maxConsecutiveErrors,
                    maxRepetitions, doomLoopThreshold, toolOutputMaxLines, toolOutputMaxBytes,
                    workingDirectory, blockedCommands,
                    compactionThreshold, microCompactKeepRecent,
                    pruneProtectTokens, pruneMinimumTokens,
                    persistSessions, sessionDirectory, autoSaveInterval,
                    enableTodoWrite, todoNagThreshold, enableFileSnapshot,
                    toolOutputDynamicTruncation, modelContextWindow
            );
        }
    }
}
