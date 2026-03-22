package com.sprinkleclaw.tool.builtin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 资源文件加载内置工具描述。
 *
 * <p>工具描述存放在 {@code tools/<toolName>.txt} 资源文件中，
 * 便于在不修改 Java 代码的情况下调整工具描述文本。</p>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
final class ToolDescriptions {

    private ToolDescriptions() {
    }

    /**
     * 加载指定工具的描述文本。
     *
     * @param toolName 工具名称（对应 {@code tools/<toolName>.txt} 资源文件）
     * @param fallback 资源文件不存在时的回退描述
     * @return 工具描述文本
     */
    static String load(String toolName, String fallback) {
        String path = "tools/" + toolName + ".txt";
        try (InputStream is = ToolDescriptions.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return fallback;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return fallback;
        }
    }
}
