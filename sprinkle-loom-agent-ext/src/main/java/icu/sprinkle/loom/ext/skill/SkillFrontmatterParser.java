package icu.sprinkle.loom.ext.skill;

import java.util.*;

/**
 * 纯手写的 YAML Frontmatter 解析器，不引入任何 YAML 库。
 *
 * <p>支持格式：</p>
 * <pre>
 * ---
 * name: code-review
 * description: Review code for quality, security, and best practices
 * tags: code, review, quality
 * ---
 *
 * # Skill Content ...
 * </pre>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class SkillFrontmatterParser {

    private SkillFrontmatterParser() {
    }

    /**
     * 解析 SKILL.md 文件的 Frontmatter 和正文。
     *
     * @param content 文件完整内容
     * @return 解析结果（frontmatter 字段 Map + body 正文）
     * @throws IllegalArgumentException 缺少 Frontmatter 分隔符或必填字段
     */
    public static ParseResult parse(String content) {
        if (!content.startsWith("---")) {
            throw new IllegalArgumentException("SKILL.md must start with YAML frontmatter (---)");
        }

        int endIndex = content.indexOf("\n---", 3);
        if (endIndex == -1) {
            throw new IllegalArgumentException("Missing closing frontmatter delimiter (---)");
        }

        String frontmatterBlock = content.substring(3, endIndex).trim();
        String body = content.substring(endIndex + 4).trim();

        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontmatterBlock.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                fields.put(key, value);
            }
        }

        return new ParseResult(fields, body);
    }

    /**
     * Frontmatter 解析结果。
     */
    public record ParseResult(Map<String, String> fields, String body) {

        /**
         * 获取必填字段，缺失时抛异常。
         */
        public String require(String key) {
            String value = fields.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required frontmatter field: " + key);
            }
            return value;
        }

        /**
         * 获取可选字段，缺失返回空字符串。
         */
        public String getOptional(String key) {
            return fields.getOrDefault(key, "");
        }

        /**
         * 将逗号分隔的字段解析为标签列表。
         */
        public List<String> getTags(String key) {
            String value = fields.getOrDefault(key, "");
            if (value.isBlank()) {
                return List.of();
            }
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
