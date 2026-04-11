package com.sprinkleclaw.llm;

import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主/备 LLM Provider 自动切换包装器。
 * <p>当主 Provider 连续失败 N 次后自动切换到备用 Provider。
 * 经过恢复窗口后尝试恢复主 Provider。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class FallbackProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackProvider.class);

    private final LlmProvider primary;
    private final LlmProvider fallback;
    private final int failureThreshold;
    private final Duration recoveryWindow;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> fallbackActivatedAt = new AtomicReference<>(null);

    /**
     * 创建 Fallback Provider。
     *
     * @param primary          主 Provider
     * @param fallback         备用 Provider
     * @param failureThreshold 连续失败多少次后切换到备用
     * @param recoveryWindow   切换后多久尝试恢复主 Provider
     */
    public FallbackProvider(LlmProvider primary, LlmProvider fallback,
                            int failureThreshold, Duration recoveryWindow) {
        this.primary = primary;
        this.fallback = fallback;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryWindow = recoveryWindow;
    }

    @Override
    public String providerId() {
        return primary.providerId() + "+" + fallback.providerId();
    }

    @Override
    public LlmCapabilities capabilities() {
        return shouldUseFallback() ? fallback.capabilities() : primary.capabilities();
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        if (shouldUseFallback()) {
            return chatWithFallback(request);
        }
        return chatWithPrimary(request);
    }

    @Override
    public ChatResponse streamChat(ChatRequest request, StreamCallback callback) throws LlmException {
        if (shouldUseFallback()) {
            return streamChatWithFallback(request, callback);
        }
        return streamChatWithPrimary(request, callback);
    }

    /**
     * 是否当前正在使用 Fallback。
     */
    public boolean isFallbackActive() {
        return shouldUseFallback();
    }

    /**
     * 获取连续失败次数。
     */
    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取主 Provider。
     */
    public LlmProvider primary() {
        return primary;
    }

    /**
     * 获取备用 Provider。
     */
    public LlmProvider fallback() {
        return fallback;
    }

    private boolean shouldUseFallback() {
        if (consecutiveFailures.get() < failureThreshold) {
            return false;
        }
        // 检查恢复窗口
        Instant activatedAt = fallbackActivatedAt.get();
        if (activatedAt != null) {
            Duration elapsed = Duration.between(activatedAt, Instant.now());
            if (elapsed.compareTo(recoveryWindow) > 0) {
                // 恢复窗口已过，尝试恢复主 Provider
                log.info("Fallback 恢复窗口已过（{}），尝试恢复主 Provider: {}",
                        elapsed, primary.providerId());
                consecutiveFailures.set(0);
                fallbackActivatedAt.set(null);
                return false;
            }
        }
        return true;
    }

    private ChatResponse chatWithPrimary(ChatRequest request) {
        try {
            ChatResponse response = primary.chat(request);
            onPrimarySuccess();
            return response;
        } catch (LlmException e) {
            return handlePrimaryFailure(e, request, false, null);
        }
    }

    private ChatResponse streamChatWithPrimary(ChatRequest request, StreamCallback callback) {
        try {
            ChatResponse response = primary.streamChat(request, callback);
            onPrimarySuccess();
            return response;
        } catch (LlmException e) {
            return handlePrimaryFailure(e, request, true, callback);
        }
    }

    private ChatResponse handlePrimaryFailure(LlmException e, ChatRequest request,
                                              boolean isStreaming, StreamCallback callback) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("主 Provider {} 失败（连续 {} 次）: {}", primary.providerId(), failures, e.getMessage());

        if (failures >= failureThreshold) {
            log.warn("连续失败 {} 次，激活 Fallback Provider: {}",
                    failures, fallback.providerId());
            fallbackActivatedAt.set(Instant.now());

            if (isStreaming && callback != null) {
                return fallback.streamChat(request, callback);
            }
            return fallback.chat(request);
        }
        // 未达阈值，继续抛出异常让上层重试
        throw e;
    }

    private ChatResponse chatWithFallback(ChatRequest request) {
        try {
            return fallback.chat(request);
        } catch (LlmException e) {
            log.error("Fallback Provider {} 也失败: {}", fallback.providerId(), e.getMessage());
            throw new LlmException(e.kind(),
                    "Both primary and fallback providers failed. Primary: "
                            + primary.providerId() + ", Fallback: " + fallback.providerId()
                            + ". Error: " + e.getMessage(), e);
        }
    }

    private ChatResponse streamChatWithFallback(ChatRequest request, StreamCallback callback) {
        try {
            return fallback.streamChat(request, callback);
        } catch (LlmException e) {
            log.error("Fallback Provider {} 也失败: {}", fallback.providerId(), e.getMessage());
            throw new LlmException(e.kind(),
                    "Both primary and fallback providers failed. Error: " + e.getMessage(), e);
        }
    }

    private void onPrimarySuccess() {
        if (consecutiveFailures.get() > 0) {
            consecutiveFailures.set(0);
            fallbackActivatedAt.set(null);
        }
    }
}
