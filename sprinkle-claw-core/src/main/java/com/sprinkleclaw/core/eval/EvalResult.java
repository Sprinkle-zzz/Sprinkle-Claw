package com.sprinkleclaw.core.eval;

import java.util.List;

/**
 * Agent 评估结果。
 *
 * @param scenario 场景名称
 * @param score    评分（0.0-1.0）
 * @param passed   各期望行为是否满足
 * @param feedback 评估 LLM 的反馈意见
 * @param output   Agent 实际输出
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public record EvalResult(
        String scenario,
        double score,
        List<Boolean> passed,
        List<String> feedback,
        String output
) {
}
