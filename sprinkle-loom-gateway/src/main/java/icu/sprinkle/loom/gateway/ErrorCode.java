package icu.sprinkle.loom.gateway;

/**
 * 网关错误码枚举。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public enum ErrorCode {

    // ===== 认证错误 =====
    AUTH_001("Authentication required"),
    AUTH_002("Invalid API key"),
    AUTH_003("JWT token expired"),
    AUTH_004("Invalid JWT token"),

    // ===== 访问控制错误 =====
    ACL_001("Access denied by ACL policy"),

    // ===== 限流错误 =====
    RATE_001("Rate limit exceeded"),
    RATE_002("Daily token quota exceeded"),

    // ===== 安全检测 =====
    SEC_001("Potential prompt injection detected"),
    SEC_002("Output validation failed"),

    // ===== 内部错误 =====
    INTERNAL("Internal gateway error");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
