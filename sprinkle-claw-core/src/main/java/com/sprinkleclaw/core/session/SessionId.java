package com.sprinkleclaw.core.session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 会话标识符值对象。
 *
 * <p>格式：{yyyyMMdd}-{random8}，如 "20260322-a1b2c3d4"。
 * 在会话创建时生成，整个生命周期内不变。</p>
 *
 * @param value 标识符字符串
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public record SessionId(String value) {

    public SessionId {
        Objects.requireNonNull(value, "sessionId value must not be null");
    }

    /**
     * 生成新的会话标识符。
     *
     * @return 新的 SessionId
     */
    public static SessionId generate() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(LocalDate.now());
        String random = UUID.randomUUID().toString().substring(0, 8);
        return new SessionId(timestamp + "-" + random);
    }

    /**
     * 从字符串值创建 SessionId。
     *
     * @param value 标识符字符串
     * @return SessionId 实例
     */
    public static SessionId of(String value) {
        return new SessionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
