package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件写入工具。
 * <p>将内容写入指定文件，自动创建不存在的父目录，覆盖已有内容。</p>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class WriteFileTool implements AgentTool {

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string",
                "description", "Path to write to (relative to workdir or absolute)"));
        props.put("content", Map.of("type", "string",
                "description", "Content to write to the file"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("path", "content"));

        return ToolDefinition.of("write_file",
                ToolDescriptions.load("write_file",
                        "Write content to a file."),
                schema);
    }

    /**
     * 写入文件内容。若父目录不存在则自动创建。
     */
    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String pathStr = (String) input.get("path");
        String content = (String) input.get("content");

        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error(name(), "No path provided");
        }
        if (content == null) {
            content = "";
        }

        Path filePath = context.workingDirectory().resolve(pathStr).normalize();

        try {
            // MVP2: 对已存在的文件进行时间戳校验
            boolean isNew = !Files.exists(filePath);
            if (!isNew) {
                String validation = FileToolHelper.validateTimestamp(context, filePath);
                if ("EXTERNALLY_MODIFIED".equals(validation)) {
                    return ToolResult.error(name(),
                            "Warning: File '" + pathStr + "' has been modified externally "
                                    + "since it was last read. Please read the file again before overwriting.");
                }
            }

            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(filePath, content);
            long bytes = Files.size(filePath);

            // MVP2: 记录时间戳 + 文件快照
            FileToolHelper.recordTimestamp(context, filePath);
            FileToolHelper.takeSnapshot(context, context.workingDirectory(), filePath,
                    isNew, "write_file: " + filePath.getFileName());

            return ToolResult.success(name(),
                    "Successfully wrote " + bytes + " bytes to " + pathStr);

        } catch (IOException e) {
            return ToolResult.error(name(), "Error writing file: " + e.getMessage());
        }
    }
}
