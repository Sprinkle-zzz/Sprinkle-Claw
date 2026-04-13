package com.sprinkleclaw.mcp.protocol;

/**
 * MCP / JSON-RPC 错误码。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class McpError {

    private McpError() {}

    // ── JSON-RPC 标准错误码 ──
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
}
