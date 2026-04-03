package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.context.AgentContext;
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
 * <h3>自适应截断（v1.1 新增）</h3>
 * <p>当启用动态截断（{@code toolOutputDynamicTruncation = true}）时，
 * {@code maxBytes} 会根据当前上下文使用率动态调整：</p>
 * <ul>
 *   <li>使用率 &lt; 50%：50KB（宽裕状态）</li>
 *   <li>使用率 50%-75%：20KB（接近阈值）</li>
 *   <li>使用率 &gt; 75%：10KB（紧张状态）</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public final class ToolOutputTruncator {

    private static final Logger log = LoggerFactory.getLogger(ToolOutputTruncator.class);

    /**
     * 宽裕状态字节上限：50KB
     */
    private static final int BYTES_RELAXED = 50 * 1024;
    /**
     * 接近阈值字节上限：20KB
     */
    private static final int BYTES_MODERATE = 20 * 1024;
    /**
     * 紧张状态字节上限：10KB
     */
    private static final int BYTES_TIGHT = 10 * 1024;

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
     * 根据上下文使用率计算动态截断字节上限。
     *
     * @param context            Agent 上下文（用于读取 cachedTokenCount）
     * @param modelContextWindow 模型上下文窗口大小
     * @return 动态计算的字节上限
     */
    public static int computeMaxOutputBytes(AgentContext context, int modelContextWindow) {
        if (modelContextWindow <= 0) {
            return BYTES_RELAXED;
        }
        int cachedTokens = context.cachedTokenCount();
        if (cachedTokens < 0) {
            return BYTES_RELAXED;
        }
        double usage = (double) cachedTokens / modelContextWindow;
        if (usage < 0.5) return BYTES_RELAXED;
        if (usage < 0.75) return BYTES_MODERATE;
        return BYTES_TIGHT;
    }

    /**
     * 若输出超限则截断并保存完整内容到临时文件，否则原样返回。
     *
     * @param toolName 工具名称（用于生成临时文件名）
     * @param output   工具原始输出
     * @return 截断后的输出（或原样返回）
     */
    public String truncateIfNeeded(String toolName, String output) {
        return truncateIfNeeded(toolName, output, maxBytes);
    }

    /**
     * 自适应截断：使用指定的字节上限判断是否截断。
     *
     * @param toolName          工具名称
     * @param output            工具原始输出
     * @param effectiveMaxBytes 当前有效的字节上限（可能由 {@link #computeMaxOutputBytes} 动态计算）
     * @return 截断后的输出（或原样返回）
     */
    public String truncateIfNeeded(String toolName, String output, int effectiveMaxBytes) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        boolean exceedsBytes = output.getBytes(StandardCharsets.UTF_8).length > effectiveMaxBytes;
        boolean exceedsLines = countLines(output) > maxLines;

        if (!exceedsBytes && !exceedsLines) {
            return output;
        }

        Path savedPath = saveFullOutput(toolName, output);
        String preview = truncate(output, effectiveMaxBytes);
        String hint = savedPath != null
                ? "\n\n[Output truncated. Full output saved to: " + savedPath
                + "]\n[Use read_file with offset/limit to view specific sections]"
                : "\n\n[Output truncated. Use more specific queries to reduce output size]";

        return preview + hint;
    }

    /**
     * 截断输出到 maxLines 行和指定字节上限以内。
     */
    private String truncate(String output, int byteLimit) {
        String[] lines = output.split("\n", -1);
        if (lines.length <= maxLines) {
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
        String linesTruncated = sb.toString();
        if (linesTruncated.getBytes(StandardCharsets.UTF_8).length > byteLimit) {
            return new String(linesTruncated.getBytes(StandardCharsets.UTF_8), 0, byteLimit, StandardCharsets.UTF_8);
        }
        return linesTruncated;
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
