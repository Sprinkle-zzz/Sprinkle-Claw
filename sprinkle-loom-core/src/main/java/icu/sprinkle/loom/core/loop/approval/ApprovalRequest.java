package icu.sprinkle.loom.core.loop.approval;

import icu.sprinkle.loom.tool.RiskLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * HITL 审批请求。
 *
 * @param requestId   请求 ID（全局唯一）
 * @param toolName    工具名称
 * @param input       工具输入参数
 * @param description 审批描述（给用户看的说明）
 * @param riskLevel   工具风险等级
 * @param timestamp   请求时间
 * @param timeout     审批超时时间
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public record ApprovalRequest(
        String requestId,
        String toolName,
        Map<String, Object> input,
        String description,
        RiskLevel riskLevel,
        Instant timestamp,
        Duration timeout
) {

    /**
     * 默认审批超时时间：5 分钟。
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
}
