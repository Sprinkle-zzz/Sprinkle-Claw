package com.sprinkleclaw.core.loop.resilience;

import java.time.Duration;

/**
 * 引擎韧性配置。
 *
 * @param maxRetries             单次错误最大重试次数
 * @param maxRetryDelay          最大重试延迟
 * @param fallbackThreshold      连续失败多少次后触发 Fallback
 * @param fallbackRecoveryWindow Fallback 激活后多久尝试恢复主模型
 * @param streamIdleTimeout      流式模式下 SSE 事件间隔超时
 * @param maxEscalationAttempts  max_output_tokens 最大升级次数
 * @param enableAutoCompaction   错误恢复时是否自动触发上下文压缩
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public record ResilienceConfig(
        int maxRetries,
        Duration maxRetryDelay,
        int fallbackThreshold,
        Duration fallbackRecoveryWindow,
        Duration streamIdleTimeout,
        int maxEscalationAttempts,
        boolean enableAutoCompaction
) {

    /**
     * 默认配置。
     */
    public static final ResilienceConfig DEFAULT = new ResilienceConfig(
            5,
            Duration.ofSeconds(30),
            3,
            Duration.ofMinutes(5),
            Duration.ofSeconds(90),
            2,
            true
    );

    /**
     * 返回默认配置。
     */
    public static ResilienceConfig defaults() {
        return DEFAULT;
    }
}
