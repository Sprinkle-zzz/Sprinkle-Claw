package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.context.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工具输出截断器，防止超长输出占用过多上下文窗口。
 *
 * <p>当输出超过 {@code maxLines} 行或 {@code maxBytes} 字节时返回截断预览。
 * 仅当 {@code workingDir} 非空（由 {@code LoomBuilder} 在 {@code enableFileTools} 时传入）时
 * 才将完整输出额外写入 {@code workingDir/.sprinkle-loom/truncated/} 并附带 {@code read_file} 提示——
 * 避免在无 file tools 场景下产生孤儿文件。</p>
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
 * <h3>大输出转文件引用（MVP3 新增）</h3>
 * <p>当输出超过 {@code largeOutputFileThreshold}（默认 100KB）时，完整输出写入文件，
 * 上下文中不保留任何预览，仅留文件路径引用。这比截断预览更节省 token。</p>
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
    private final int largeOutputFileThreshold;
    private final Path truncateDir;
    private final Path largeOutputDir;

    /**
     * @param maxLines                 最大输出行数（默认 2000）
     * @param maxBytes                 最大输出字节数（默认 50KB）
     * @param largeOutputFileThreshold 超过此大小的输出转为文件引用（字节，0 表示禁用）
     * @param workingDir               工作目录；{@code null} 表示仅做内存截断，不写盘（无 file tools 场景）
     */
    public ToolOutputTruncator(int maxLines, int maxBytes, int largeOutputFileThreshold, Path workingDir) {
        this.maxLines = maxLines;
        this.maxBytes = maxBytes;
        this.largeOutputFileThreshold = largeOutputFileThreshold;
        this.truncateDir = workingDir != null
                ? workingDir.resolve(".sprinkle-loom").resolve("truncated") : null;
        this.largeOutputDir = workingDir != null
                ? workingDir.resolve(".sprinkle-loom").resolve("large-outputs") : null;
    }

    /**
     * @param maxLines   最大输出行数（默认 2000）
     * @param maxBytes   最大输出字节数（默认 50KB）
     * @param workingDir 工作目录（截断文件存放在其子目录下）
     */
    public ToolOutputTruncator(int maxLines, int maxBytes, Path workingDir) {
        this(maxLines, maxBytes, 0, workingDir);
    }

    /**
     * 使用默认阈值创建截断器（2000 行 / 50KB）。
     *
     * @param workingDir 工作目录
     */
    public ToolOutputTruncator(Path workingDir) {
        this(2000, 50 * 1024, 0, workingDir);
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
        if (usage < 0.5) {
            return BYTES_RELAXED;
        }
        if (usage < 0.75) {
            return BYTES_MODERATE;
        }
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

        int bytes = output.getBytes(StandardCharsets.UTF_8).length;

        // MVP3：超大输出转文件引用（不保留预览）
        if (largeOutputFileThreshold > 0 && bytes > largeOutputFileThreshold) {
            return saveAsFileReference(toolName, output, bytes);
        }

        boolean exceedsBytes = bytes > effectiveMaxBytes;
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
     * 将超大输出保存到文件，返回仅含路径引用的摘要（不保留任何预览）。
     *
     * @param toolName 工具名称
     * @param output   完整输出
     * @param bytes    输出字节数
     * @return 文件路径引用文本
     */
    private String saveAsFileReference(String toolName, String output, int bytes) {
        if (largeOutputDir == null) {
            // 无 workingDir（未启用 file tools）：降级为内存截断预览，不写盘
            return truncate(output, maxBytes) + "\n\n[Output truncated]";
        }
        try {
            Files.createDirectories(largeOutputDir);
            String filename = toolName + "_" + System.currentTimeMillis() + ".txt";
            Path file = largeOutputDir.resolve(filename);
            Files.writeString(file, output, StandardCharsets.UTF_8);

            int lines = countLines(output);
            log.debug("[ToolOutputTruncator] 超大输出已保存: {} ({} bytes, {} lines)", file, bytes, lines);

            return "[Output saved to: " + file + "] "
                    + "(size: " + formatBytes(bytes) + ", lines: " + lines + ")\n"
                    + "Use read_file with offset/limit to view specific sections.";
        } catch (IOException e) {
            log.warn("[ToolOutputTruncator] 超大输出保存失败，降级为截断预览: {}", e.getMessage());
            // fallback 到截断预览
            Path savedPath = saveFullOutput(toolName, output);
            String preview = truncate(output, maxBytes);
            String hint = savedPath != null
                    ? "\n\n[Output truncated. Full output saved to: " + savedPath + "]"
                    : "\n\n[Output truncated]";
            return preview + hint;
        }
    }

    /**
     * 格式化字节数为人类可读格式。
     */
    private static String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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
        if (truncateDir == null) {
            return null;
        }
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
