package com.sprinkleclaw.core.context;

/**
 * 上下文压缩操作的结果记录。
 *
 * @param type            压缩类型
 * @param tokensBefore    压缩前 token 数
 * @param tokensAfter     压缩后 token 数
 * @param messagesRemoved 被移除/替换的消息数
 * @param summary         AutoCompactor 生成的摘要文本（Micro/Prune 压缩为 null）
 *
 * @author sprinkle
 * @since 2026/3/23
 */
public record CompactionResult(
        CompactionType type,
        int tokensBefore,
        int tokensAfter,
        int messagesRemoved,
        String summary
) {

    /**
     * 压缩类型枚举。
     */
    public enum CompactionType {
        /**
         * 微压缩：替换旧工具结果为占位符
         */
        MICRO,
        /**
         * 裁剪压缩：清除保护区外的旧工具输出（不调用 LLM）
         */
        PRUNE,
        /**
         * 自动压缩：LLM 生成结构化摘要
         */
        AUTO,
        /**
         * 手动压缩：通过 compact 工具触发
         */
        MANUAL
    }

    /**
     * 本次压缩节省的 token 数。
     *
     * @return 节省量（tokensBefore - tokensAfter）
     */
    public int tokensSaved() {
        return tokensBefore - tokensAfter;
    }
}
