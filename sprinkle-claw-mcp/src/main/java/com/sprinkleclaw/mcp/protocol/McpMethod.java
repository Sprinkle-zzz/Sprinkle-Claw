package com.sprinkleclaw.mcp.protocol;

/**
 * MCP 协议方法名常量。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpMethod {

    private McpMethod() {}

    // ── Lifecycle ──
    public static final String INITIALIZE = "initialize";
    public static final String INITIALIZED = "notifications/initialized";
    public static final String PING = "ping";

    // ── Tools ──
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";

    // ── Resources ──
    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCES_READ = "resources/read";

    // ── Prompts ──
    public static final String PROMPTS_LIST = "prompts/list";
    public static final String PROMPTS_GET = "prompts/get";

    // ── Notifications ──
    public static final String CANCELLED = "notifications/cancelled";
    public static final String PROGRESS = "notifications/progress";
}
