package com.sprinkleclaw.core.session;

/**
 * 会话存储异常。
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public class SessionStoreException extends RuntimeException {

    public SessionStoreException(String message) {
        super(message);
    }

    public SessionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
