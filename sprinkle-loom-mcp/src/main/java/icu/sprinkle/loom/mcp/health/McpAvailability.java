package icu.sprinkle.loom.mcp.health;

/**
 * MCP 官方 SDK 运行时可用性检测。
 * <p>SDK 在 {@code sprinkle-loom-mcp} 中以 {@code <optional>true</optional>} 方式声明，
 * 终端用户必须显式引入 {@code io.modelcontextprotocol.sdk:mcp} 才能真正使用 MCP 桥接能力。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public final class McpAvailability {

    private static final String PROBE_CLASS = "io.modelcontextprotocol.client.McpSyncClient";

    private static final boolean AVAILABLE;

    static {
        boolean ok;
        try {
            Class.forName(PROBE_CLASS, false, McpAvailability.class.getClassLoader());
            ok = true;
        } catch (ClassNotFoundException | LinkageError e) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    private McpAvailability() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static void requireAvailable() {
        if (!AVAILABLE) {
            throw new IllegalStateException(
                    "MCP SDK not on classpath. Add dependency: io.modelcontextprotocol.sdk:mcp");
        }
    }
}
