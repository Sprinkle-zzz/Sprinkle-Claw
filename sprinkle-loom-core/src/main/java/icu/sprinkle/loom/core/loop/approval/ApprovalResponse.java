package icu.sprinkle.loom.core.loop.approval;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * HITL 审批响应。
 *
 * @param approved      是否批准
 * @param reason        拒绝原因（批准时为空）
 * @param modifiedInput 审批时修改的工具输入参数（未修改时为空）
 * @param respondedAt   响应时间
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public record ApprovalResponse(
        boolean approved,
        Optional<String> reason,
        Optional<Map<String, Object>> modifiedInput,
        Instant respondedAt
) {

    /**
     * 创建批准响应。
     */
    public static ApprovalResponse approve() {
        return new ApprovalResponse(true, Optional.empty(), Optional.empty(), Instant.now());
    }

    /**
     * 创建带修改输入的批准响应。
     */
    public static ApprovalResponse approveWithModifiedInput(Map<String, Object> modifiedInput) {
        return new ApprovalResponse(true, Optional.empty(), Optional.of(modifiedInput), Instant.now());
    }

    /**
     * 创建拒绝响应。
     */
    public static ApprovalResponse deny(String reason) {
        return new ApprovalResponse(false, Optional.of(reason), Optional.empty(), Instant.now());
    }

    /**
     * 创建超时拒绝响应。
     */
    public static ApprovalResponse timeout() {
        return new ApprovalResponse(false, Optional.of("Approval timed out"), Optional.empty(), Instant.now());
    }
}
