package com.sprinkleclaw.core.context;

/**
 * 上下文窗口管理器，负责 Token 估算和上下文压缩。
 *
 * <p>MVP1 为空实现骨架，MVP2 将实现三层压缩策略：</p>
 * <ul>
 *   <li>Layer 1: MicroCompactor — 替换旧 tool_result 为占位符</li>
 *   <li>Layer 1.5: PruneCompactor — 裁剪旧输出（保护区 40K + 最小裁剪 20K）</li>
 *   <li>Layer 2: AutoCompactor — LLM 结构化摘要</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public final class ContextManager {

    /**
     * MVP1: 不做压缩，直接返回。
     * MVP2: 检查 Token 阈值，触发 micro/prune/auto compaction。
     *
     * @param context Agent 上下文
     */
    public void compactIfNeeded(AgentContext context) {
        // No-op in MVP1
    }
}
