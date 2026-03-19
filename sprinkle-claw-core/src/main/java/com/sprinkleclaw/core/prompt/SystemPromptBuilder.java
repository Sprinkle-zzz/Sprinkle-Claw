package com.sprinkleclaw.core.prompt;

import com.sprinkleclaw.protocol.tool.ToolDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * 系统提示词构建器，将工具定义、环境信息和自定义提示组装为完整的系统提示。
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class SystemPromptBuilder {

    private SystemPromptBuilder() {
    }

    /**
     * 构建系统提示词。
     *
     * @param tools            可用工具列表
     * @param workingDirectory 工作目录
     * @param customPrompt     用户自定义提示（可为空）
     * @return 完整的系统提示词
     */
    public static String build(List<ToolDefinition> tools, Path workingDirectory, String customPrompt) {
        var sb = new StringBuilder();

        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append(customPrompt).append("\n\n");
        }

        sb.append("# Environment\n");
        sb.append("Working directory: ").append(workingDirectory.toAbsolutePath()).append('\n');
        sb.append("Platform: ").append(System.getProperty("os.name")).append('\n');

        if (!tools.isEmpty()) {
            sb.append("\n# Available Tools\n");
            for (ToolDefinition tool : tools) {
                sb.append("- **").append(tool.name()).append("**: ").append(tool.description()).append('\n');
            }
        }

        sb.append("\n# Important Rules\n");
        sb.append("- Always use tools to interact with the system\n");
        sb.append("- Wait for tool results before proceeding\n");
        sb.append("- If a tool call fails, try a different approach\n");

        return sb.toString();
    }
}
