package com.sprinkleclaw.gateway.filter;

/**
 * 过滤器执行顺序常量。
 * <p>数值越小越先执行。Agent 执行发生在 pre 和 post 阶段之间。</p>
 *
 * <pre>
 * Pre-filters (升序执行):
 *   ACL(100) → AUTH(200) → TENANT(300) → RATE_LIMIT(400)
 *   → PROMPT_INJECTION_GUARD(500) → AUDIT_PRE(600)
 *
 * [Agent 执行]
 *
 * Post-filters (降序执行):
 *   TOKEN_METERING(700) → OUTPUT_VALIDATOR(800) → AUDIT_POST(900)
 * </pre>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class FilterOrder {

    public static final int ACL = 100;
    public static final int AUTH = 200;
    public static final int TENANT = 300;
    public static final int RATE_LIMIT = 400;
    public static final int PROMPT_INJECTION_GUARD = 500;
    public static final int AUDIT_PRE = 600;

    // --- Agent 执行 ---

    public static final int TOKEN_METERING = 700;
    public static final int OUTPUT_VALIDATOR = 800;
    public static final int AUDIT_POST = 900;

    private FilterOrder() {}
}
