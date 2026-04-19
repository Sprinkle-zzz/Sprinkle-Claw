package com.sprinkleclaw.gateway.metrics;

import java.time.Instant;

/**
 * Token 用量事件。
 *
 * @param tenantId     租户标识
 * @param userId       用户标识
 * @param inputTokens  输入 token 数
 * @param outputTokens 输出 token 数
 * @param timestamp    时间戳
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record UsageEvent(
        String tenantId,
        String userId,
        int inputTokens,
        int outputTokens,
        Instant timestamp
) {
}
