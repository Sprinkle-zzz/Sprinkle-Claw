package icu.sprinkle.loom.gateway.audit;

import java.time.Instant;

/**
 * 审计事件。
 *
 * @param requestId 请求 ID
 * @param tenantId  租户标识
 * @param userId    用户标识
 * @param action    操作类型（REQUEST / RESPONSE）
 * @param message   用户消息（REQUEST 阶段）或 Agent 输出（RESPONSE 阶段）
 * @param inputTokens  输入 token（仅 RESPONSE 阶段）
 * @param outputTokens 输出 token（仅 RESPONSE 阶段）
 * @param elapsedMs    耗时毫秒（仅 RESPONSE 阶段）
 * @param timestamp 时间戳
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record AuditEvent(
        String requestId,
        String tenantId,
        String userId,
        String action,
        String message,
        int inputTokens,
        int outputTokens,
        long elapsedMs,
        Instant timestamp
) {
}
