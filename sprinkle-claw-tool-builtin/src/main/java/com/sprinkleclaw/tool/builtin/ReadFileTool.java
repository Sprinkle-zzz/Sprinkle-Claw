package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件读取工具。
 * <p>支持读取整个文件或通过 offset/limit 参数读取指定范围的行，输出带行号。</p>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class ReadFileTool implements AgentTool {

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string",
                "description", "Path to the file (relative to workdir or absolute)"));
        props.put("offset", Map.of("type", "integer",
                "description", "Line number to start reading from (1-based, optional)"));
        props.put("limit", Map.of("type", "integer",
                "description", "Maximum number of lines to read (optional)"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("path"));

        return ToolDefinition.of("read_file",
                ToolDescriptions.load("read_file",
                        "Read the contents of a file."),
                schema);
    }

    /**
     * 读取文件内容，支持行号偏移和行数限制。
     * <p>输出格式为 {@code 行号|内容}，便于 LLM 引用具体行。</p>
     */
    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String pathStr = (String) input.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error(name(), "No path provided");
        }

        Path filePath = context.workingDirectory().resolve(pathStr).normalize();

        try {
            List<String> lines = Files.readAllLines(filePath);
            int offset = input.containsKey("offset")
                    ? ((Number) input.get("offset")).intValue() - 1 : 0;
            int limit = input.containsKey("limit")
                    ? ((Number) input.get("limit")).intValue() : lines.size();

            offset = Math.clamp(offset, 0, lines.size());
            limit = Math.min(limit, lines.size() - offset);

            var sb = new StringBuilder();
            for (int i = offset; i < offset + limit; i++) {
                sb.append(String.format("%6d|%s%n", i + 1, lines.get(i)));
            }
            return ToolResult.success(name(), sb.toString());

        } catch (NoSuchFileException e) {
            return ToolResult.error(name(), "File not found: " + pathStr);
        } catch (IOException e) {
            return ToolResult.error(name(), "Error reading file: " + e.getMessage());
        }
    }
}
