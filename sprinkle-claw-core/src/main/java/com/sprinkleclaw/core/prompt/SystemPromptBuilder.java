package com.sprinkleclaw.core.prompt;

import com.sprinkleclaw.protocol.tool.ToolDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * 系统提示词构建器，将用户自定义提示与可选的工具/环境段组装为完整的系统提示。
 *
 * <p>遵循"按需注入"原则：</p>
 * <ul>
 *   <li>工具列表为空时，不注入 {@code # Available Tools} 和 {@code # Important Rules}
 *       （纯 chat agent 的 system prompt = 用户传入的 customPrompt）</li>
 *   <li>{@code # Environment}（工作目录 + 平台信息）仅在调用方明确请求时注入，
 *       由 ClawBuilder 在启用文件类工具或 bash 工具时自动设为 {@code true}</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class SystemPromptBuilder {

    private SystemPromptBuilder() {
    }

    /**
     * 构建系统提示词（不注入 Environment 段）。
     *
     * @param tools        可用工具列表，空列表表示纯 chat agent
     * @param customPrompt 用户自定义提示（可为空）
     * @return 完整的系统提示词
     */
    public static String build(List<ToolDefinition> tools, String customPrompt) {
        return build(tools, null, customPrompt, false);
    }

    /**
     * 构建系统提示词。
     *
     * @param tools              可用工具列表，空列表表示纯 chat agent
     * @param workingDirectory   工作目录（仅在 {@code includeEnvironment=true} 时使用）
     * @param customPrompt       用户自定义提示（可为空）
     * @param includeEnvironment 是否注入 {@code # Environment} 段（仅文件类/bash 工具启用时建议为 true）
     * @return 完整的系统提示词
     */
    public static String build(List<ToolDefinition> tools, Path workingDirectory,
                               String customPrompt, boolean includeEnvironment) {
        var sb = new StringBuilder();

        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append(customPrompt);
        }

        if (includeEnvironment && workingDirectory != null) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("# Environment\n");
            sb.append("Working directory: ").append(workingDirectory.toAbsolutePath()).append('\n');
            sb.append("Platform: ").append(System.getProperty("os.name")).append('\n');
        }

        if (!tools.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("# Available Tools\n");
            for (ToolDefinition tool : tools) {
                sb.append("- **").append(tool.name()).append("**: ").append(tool.description()).append('\n');
            }

            sb.append("\n# Important Rules\n");
            sb.append("- Use the provided tools when they are needed to complete the task\n");
            sb.append("- Wait for tool results before proceeding\n");
            sb.append("- If a tool call fails, try a different approach\n");
        }

        return sb.toString();
    }
}
