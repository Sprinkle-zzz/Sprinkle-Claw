package com.sprinkleclaw.core.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工具输出截断器，防止超长输出占用过多上下文窗口。
 *
 * <p>当输出超过 {@code maxLines} 行或 {@code maxBytes} 字节时：</p>
 * <ol>
 *   <li>将完整输出保存到临时文件（{@code .sprinkle-claw/truncated/}）</li>
 *   <li>返回截断后的预览文本，附带提示 LLM 使用 {@code read_file} 分段读取</li>
 * </ol>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public final class ToolOutputTruncator {

    private static final Logger log = LoggerFactory.getLogger(ToolOutputTruncator.class);

    private final int maxLines;
    private final int maxBytes;
    private final Path truncateDir;

    /**
     * @param maxLines   最大输出行数（默认 2000）
     * @param maxBytes   最大输出字节数（默认 50KB）
     * @param workingDir 工作目录（截断文件存放在其子目录下）
     */
    public ToolOutputTruncator(int maxLines, int maxBytes, Path workingDir) {
        this.maxLines = maxLines;
        this.maxBytes = maxBytes;
        this.truncateDir = workingDir.resolve(".sprinkle-claw").resolve("truncated");
    }

    /**
     * 使用默认阈值创建截断器（2000 行 / 50KB）。
     *
     * @param workingDir 工作目录
     */
    public ToolOutputTruncator(Path workingDir) {
        this(2000, 50 * 1024, workingDir);
    }

    /**
     * 若输出超限则截断并保存完整内容到临时文件，否则原样返回。
     *
     * @param toolName 工具名称（用于生成临时文件名）
     * @param output   工具原始输出
     * @return 截断后的输出（或原样返回）
     */
    public String truncateIfNeeded(String toolName, String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        boolean exceedsBytes = output.getBytes(StandardCharsets.UTF_8).length > maxBytes;
        boolean exceedsLines = countLines(output) > maxLines;

        if (!exceedsBytes && !exceedsLines) {
            return output;
        }

        Path savedPath = saveFullOutput(toolName, output);
        String preview = truncate(output);
        String hint = savedPath != null
                ? "\n\n[Output truncated. Full output saved to: " + savedPath
                + "]\n[Use read_file with offset/limit to view specific sections]"
                : "\n\n[Output truncated. Use more specific queries to reduce output size]";

        return preview + hint;
    }

    /**
     * 截断输出到 maxLines 行以内。
     */
    private String truncate(String output) {
        String[] lines = output.split("\n", -1);
        if (lines.length <= maxLines) {
            int byteLimit = maxBytes;
            if (output.getBytes(StandardCharsets.UTF_8).length <= byteLimit) {
                return output;
            }
            return new String(output.getBytes(StandardCharsets.UTF_8), 0, byteLimit, StandardCharsets.UTF_8);
        }

        var sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * 将完整输出保存到临时文件。
     */
    private Path saveFullOutput(String toolName, String output) {
        try {
            Files.createDirectories(truncateDir);
            String fileName = toolName + "_" + System.currentTimeMillis() + ".txt";
            Path filePath = truncateDir.resolve(fileName);
            Files.writeString(filePath, output);
            log.debug("截断输出已保存到: {}", filePath);
            return filePath;
        } catch (IOException e) {
            log.warn("保存截断输出失败: {}", e.getMessage());
            return null;
        }
    }

    private static int countLines(String text) {
        int count = 1;
        int idx = 0;
        while ((idx = text.indexOf('\n', idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }
}
