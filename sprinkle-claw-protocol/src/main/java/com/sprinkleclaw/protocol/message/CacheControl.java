package com.sprinkleclaw.protocol.message;

/**
 * Prompt Caching 控制标记。
 * <p>用于标记 {@link ContentBlock} 是否参与 LLM Provider 的 Prompt Caching。</p>
 *
 * @author sprinkle
 * @since 0.8.0 (MVP7)
 */
public sealed interface CacheControl {

    CacheControl NONE = new None();

    static CacheControl ephemeral() {
        return new Ephemeral(300);
    }

    static CacheControl ephemeral(int ttlSeconds) {
        return new Ephemeral(ttlSeconds);
    }

    record None() implements CacheControl {}

    record Ephemeral(int ttlSeconds) implements CacheControl {
        public Ephemeral {
            if (ttlSeconds <= 0) ttlSeconds = 300;
        }
    }
}
