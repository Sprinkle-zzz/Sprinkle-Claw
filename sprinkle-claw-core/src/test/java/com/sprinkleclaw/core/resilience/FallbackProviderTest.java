package com.sprinkleclaw.core.resilience;

import com.sprinkleclaw.llm.FallbackProvider;
import com.sprinkleclaw.llm.LlmException;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackProviderTest {

    private static ChatResponse ok(String text) {
        return new ChatResponse(
                List.of(new ContentBlock.TextBlock(text)),
                StopReason.END_TURN, new Usage(10, 20), "test-model"
        );
    }

    @Test
    void usePrimaryWhenHealthy() {
        LlmProvider primary = r -> ok("primary");
        LlmProvider fallback = r -> ok("fallback");
        var provider = new FallbackProvider(primary, fallback, 3, Duration.ofMinutes(5));

        var result = provider.chat(null);
        assertThat(result.textContent()).isEqualTo("primary");
        assertThat(provider.isFallbackActive()).isFalse();
    }

    @Test
    void switchToFallbackAfterThreshold() {
        var callCount = new AtomicInteger(0);
        LlmProvider primary = r -> {
            callCount.incrementAndGet();
            throw new LlmException(LlmException.ErrorKind.SERVER_ERROR, "server error");
        };
        LlmProvider fallback = r -> ok("fallback");
        var provider = new FallbackProvider(primary, fallback, 3, Duration.ofMinutes(5));

        // 前两次失败应直接抛出（未达阈值）
        assertThatThrownBy(() -> provider.chat(null)).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> provider.chat(null)).isInstanceOf(LlmException.class);
        assertThat(provider.isFallbackActive()).isFalse();

        // 第三次失败达到阈值，自动切换到 fallback
        var result = provider.chat(null);
        assertThat(result.textContent()).isEqualTo("fallback");
        assertThat(provider.isFallbackActive()).isTrue();
    }

    @Test
    void primarySuccessResetCounter() {
        var failCount = new AtomicInteger(0);
        LlmProvider primary = r -> {
            if (failCount.incrementAndGet() <= 2) {
                throw new LlmException(LlmException.ErrorKind.SERVER_ERROR, "error");
            }
            return ok("primary recovered");
        };
        LlmProvider fallback = r -> ok("fallback");
        var provider = new FallbackProvider(primary, fallback, 5, Duration.ofMinutes(5));

        // 前两次失败
        assertThatThrownBy(() -> provider.chat(null));
        assertThatThrownBy(() -> provider.chat(null));
        assertThat(provider.consecutiveFailures()).isEqualTo(2);

        // 第三次成功，重置计数
        var result = provider.chat(null);
        assertThat(result.textContent()).isEqualTo("primary recovered");
        assertThat(provider.consecutiveFailures()).isEqualTo(0);
    }

    @Test
    void providerIdCombined() {
        LlmProvider primary = r -> ok("p");
        LlmProvider fallback = r -> ok("f");
        var provider = new FallbackProvider(primary, fallback, 3, Duration.ofMinutes(5));
        assertThat(provider.providerId()).isEqualTo("custom+custom");
    }
}
