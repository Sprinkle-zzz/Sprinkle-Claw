package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 文件编辑工具，支持多层回退匹配策略。
 *
 * <p>LLM 生成的 {@code old_string} 经常与文件实际内容存在微小差异（空白、缩进等），
 * 仅做精确匹配的成功率远低于预期。本工具实现 5 层回退匹配策略，依次尝试直到成功。</p>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class EditFileTool implements AgentTool {

    /**
     * 字符串匹配策略函数式接口。
     * 给定文件内容和查找字符串，返回实际匹配到的候选字符串流。
     */
    @FunctionalInterface
    interface Replacer {
        Stream<String> candidates(String content, String find);
    }

    /**
     * 5 层回退策略，按优先级排列。
     */
    private static final List<Replacer> REPLACERS = List.of(
            new SimpleReplacer(),
            new LineTrimmedReplacer(),
            new WhitespaceNormalizedReplacer(),
            new IndentationFlexibleReplacer(),
            new BlockAnchorReplacer()
    );

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string",
                "description", "Path to the file to edit"));
        props.put("old_string", Map.of("type", "string",
                "description", "The exact string to find and replace (must be unique in the file)"));
        props.put("new_string", Map.of("type", "string",
                "description", "The replacement string"));
        props.put("replace_all", Map.of("type", "boolean",
                "description", "Replace all occurrences (default false)"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("path", "old_string", "new_string"));

        return ToolDefinition.of("edit_file",
                ToolDescriptions.load("edit_file",
                        "Edit a file by replacing an exact string match."),
                schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String pathStr = (String) input.get("path");
        String oldString = (String) input.get("old_string");
        String newString = (String) input.get("new_string");
        boolean replaceAll = Boolean.TRUE.equals(input.get("replace_all"));

        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error(name(), "No path provided");
        }
        if (oldString == null) {
            return ToolResult.error(name(), "No old_string provided");
        }
        if (newString == null) {
            newString = "";
        }
        if (oldString.equals(newString)) {
            return ToolResult.error(name(),
                    "old_string and new_string are identical, no changes to apply.");
        }

        Path filePath = context.workingDirectory().resolve(pathStr).normalize();

        // MVP2: 时间戳校验
        String validation = FileToolHelper.validateTimestamp(context, filePath);
        if ("EXTERNALLY_MODIFIED".equals(validation)) {
            return ToolResult.error(name(),
                    "Warning: File '" + pathStr + "' has been modified externally "
                            + "since it was last read. Please read the file again before editing.");
        }

        try {
            String content = Files.readString(filePath);
            String ending = content.contains("\r\n") ? "\r\n" : "\n";
            String normalizedOld = normalizeLineEndings(oldString, ending);
            String normalizedNew = normalizeLineEndings(newString, ending);

            return doReplace(content, normalizedOld, normalizedNew, replaceAll,
                    filePath, pathStr, context);

        } catch (NoSuchFileException e) {
            return ToolResult.error(name(), "File not found: " + pathStr);
        } catch (IOException e) {
            return ToolResult.error(name(), "Error editing file: " + e.getMessage());
        }
    }

    /**
     * 依次尝试所有 Replacer，找到唯一匹配后执行替换。
     */
    private ToolResult doReplace(String content, String oldString, String newString,
                                 boolean replaceAll, Path filePath, String pathStr,
                                 ToolContext context)
            throws IOException {
        boolean anyFound = false;

        for (Replacer replacer : REPLACERS) {
            List<String> matches = replacer.candidates(content, oldString).toList();

            for (String match : matches) {
                int idx = content.indexOf(match);
                if (idx == -1) {
                    continue;
                }
                anyFound = true;

                if (replaceAll) {
                    String updated = content.replace(match, newString);
                    Files.writeString(filePath, updated);
                    afterSuccessfulEdit(context, filePath, pathStr, "replace_all");
                    return ToolResult.success(name(), "Successfully edited " + pathStr);
                }

                int lastIdx = content.lastIndexOf(match);
                if (idx != lastIdx) {
                    continue;
                }

                String updated = content.substring(0, idx) + newString
                        + content.substring(idx + match.length());
                Files.writeString(filePath, updated);
                afterSuccessfulEdit(context, filePath, pathStr, "replace");
                return ToolResult.success(name(), "Successfully edited " + pathStr);
            }
        }

        if (!anyFound) {
            return ToolResult.error(name(),
                    "old_string not found in " + pathStr + ". "
                            + "Make sure the string matches exactly (including whitespace and indentation).");
        }
        return ToolResult.error(name(),
                "old_string found multiple times in " + pathStr + ". "
                        + "Provide more surrounding context to ensure a unique match.");
    }

    /**
     * 编辑成功后记录时间戳和文件快照。
     */
    private void afterSuccessfulEdit(ToolContext context, Path filePath,
                                     String pathStr, String strategy) {
        FileToolHelper.recordTimestamp(context, filePath);
        FileToolHelper.takeSnapshot(context, context.workingDirectory(), filePath,
                false, "edit_file: " + filePath.getFileName() + " (" + strategy + ")");
    }

    private static String normalizeLineEndings(String text, String ending) {
        String normalized = text.replace("\r\n", "\n");
        if ("\r\n".equals(ending)) {
            normalized = normalized.replace("\n", "\r\n");
        }
        return normalized;
    }

    // ==================== Replacer 实现 ====================

    /**
     * 1. 精确匹配（无任何规范化）。
     */
    static final class SimpleReplacer implements Replacer {
        @Override
        public Stream<String> candidates(String content, String find) {
            return content.contains(find) ? Stream.of(find) : Stream.empty();
        }
    }

    /**
     * 2. 逐行 trim 后匹配（处理行首/行尾空白差异）。
     */
    static final class LineTrimmedReplacer implements Replacer {
        @Override
        public Stream<String> candidates(String content, String find) {
            String[] contentLines = content.split("\n", -1);
            String[] findLines = find.split("\n", -1);
            if (findLines.length == 0) {
                return Stream.empty();
            }

            List<String> results = new ArrayList<>();
            for (int i = 0; i <= contentLines.length - findLines.length; i++) {
                boolean matches = true;
                for (int j = 0; j < findLines.length; j++) {
                    if (!contentLines[i + j].trim().equals(findLines[j].trim())) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    results.add(joinLines(contentLines, i, findLines.length));
                }
            }
            return results.stream();
        }
    }

    /**
     * 3. 所有空白归一化为单空格后匹配。
     */
    static final class WhitespaceNormalizedReplacer implements Replacer {
        @Override
        public Stream<String> candidates(String content, String find) {
            String normalizedFind = find.replaceAll("\\s+", " ").trim();
            if (normalizedFind.isEmpty()) {
                return Stream.empty();
            }

            String[] contentLines = content.split("\n", -1);
            String[] findLines = find.split("\n", -1);

            List<String> results = new ArrayList<>();
            for (int i = 0; i <= contentLines.length - findLines.length; i++) {
                String block = joinLines(contentLines, i, findLines.length);
                if (block.replaceAll("\\s+", " ").trim().equals(normalizedFind)) {
                    results.add(block);
                }
            }
            return results.stream();
        }
    }

    /**
     * 4. 移除最小公共缩进后匹配（处理缩进深度差异）。
     */
    static final class IndentationFlexibleReplacer implements Replacer {
        @Override
        public Stream<String> candidates(String content, String find) {
            String normalizedFind = removeMinIndent(find);
            if (normalizedFind.isBlank()) {
                return Stream.empty();
            }

            String[] contentLines = content.split("\n", -1);
            String[] findLines = find.split("\n", -1);

            List<String> results = new ArrayList<>();
            for (int i = 0; i <= contentLines.length - findLines.length; i++) {
                String block = joinLines(contentLines, i, findLines.length);
                if (removeMinIndent(block).equals(normalizedFind)) {
                    results.add(block);
                }
            }
            return results.stream();
        }

        static String removeMinIndent(String text) {
            String[] lines = text.split("\n", -1);
            int minIndent = Integer.MAX_VALUE;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') {
                    indent++;
                }
                minIndent = Math.min(minIndent, indent);
            }
            if (minIndent == 0 || minIndent == Integer.MAX_VALUE) {
                return text;
            }

            final int trim = minIndent;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                if (lines[i].length() > trim) {
                    sb.append(lines[i].substring(trim));
                } else {
                    sb.append(lines[i].stripLeading());
                }
            }
            return sb.toString();
        }
    }

    /**
     * 5. 首尾行锚定 + 中间行 Levenshtein 相似度匹配。
     */
    static final class BlockAnchorReplacer implements Replacer {
        private static final double SINGLE_CANDIDATE_THRESHOLD = 0.0;
        private static final double MULTI_CANDIDATE_THRESHOLD = 0.3;

        @Override
        public Stream<String> candidates(String content, String find) {
            String[] contentLines = content.split("\n", -1);
            String[] findLines = find.split("\n", -1);
            if (findLines.length < 3) {
                return Stream.empty();
            }

            String firstLine = findLines[0].trim();
            String lastLine = findLines[findLines.length - 1].trim();

            List<int[]> anchors = new ArrayList<>();
            for (int i = 0; i < contentLines.length; i++) {
                if (!contentLines[i].trim().equals(firstLine)) {
                    continue;
                }
                for (int j = i + 2; j < contentLines.length; j++) {
                    if (contentLines[j].trim().equals(lastLine)) {
                        anchors.add(new int[]{i, j});
                        break;
                    }
                }
            }

            if (anchors.isEmpty()) {
                return Stream.empty();
            }

            double threshold = anchors.size() == 1
                    ? SINGLE_CANDIDATE_THRESHOLD : MULTI_CANDIDATE_THRESHOLD;

            return anchors.stream()
                    .filter(a -> computeSimilarity(contentLines, findLines, a[0], a[1]) >= threshold)
                    .sorted((a, b) -> Double.compare(
                            computeSimilarity(contentLines, findLines, b[0], b[1]),
                            computeSimilarity(contentLines, findLines, a[0], a[1])))
                    .limit(1)
                    .map(a -> joinLines(contentLines, a[0], a[1] - a[0] + 1));
        }

        private static double computeSimilarity(String[] contentLines, String[] findLines,
                                                int startIdx, int endIdx) {
            int blockLen = endIdx - startIdx + 1;
            if (blockLen <= 2 || findLines.length <= 2) {
                return 1.0;
            }

            int totalChars = 0;
            int totalDistance = 0;
            int compareLen = Math.min(blockLen - 2, findLines.length - 2);

            for (int i = 0; i < compareLen; i++) {
                String contentLine = contentLines[startIdx + 1 + i].trim();
                String findLine = findLines[1 + i].trim();
                totalChars += Math.max(contentLine.length(), findLine.length());
                totalDistance += levenshtein(contentLine, findLine);
            }

            return totalChars == 0 ? 1.0 : 1.0 - ((double) totalDistance / totalChars);
        }

        /**
         * Levenshtein 编辑距离计算。
         */
        static int levenshtein(String a, String b) {
            int m = a.length();
            int n = b.length();
            int[] prev = new int[n + 1];
            int[] curr = new int[n + 1];

            for (int j = 0; j <= n; j++) {
                prev[j] = j;
            }

            for (int i = 1; i <= m; i++) {
                curr[0] = i;
                for (int j = 1; j <= n; j++) {
                    int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                    curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                }
                int[] temp = prev;
                prev = curr;
                curr = temp;
            }
            return prev[n];
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从行数组中连接指定范围的行。
     */
    static String joinLines(String[] lines, int start, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + count && i < lines.length; i++) {
            if (i > start) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
