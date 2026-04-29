package icu.sprinkle.loom.ext.subagent;

/**
 * 子 Agent 执行结果（内部使用，丢弃子 Agent 上下文后仅保留摘要）。
 *
 * @param summary             子 Agent 最终文本输出
 * @param iterationsUsed      实际消耗的迭代轮次
 * @param estimatedTokensUsed 估算消耗的 token 数
 * @param completedNormally   是否正常结束（非超时/超轮次/异常）
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public record SubAgentResult(
        String summary,
        int iterationsUsed,
        int estimatedTokensUsed,
        boolean completedNormally
) {}
