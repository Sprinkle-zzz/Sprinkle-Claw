package icu.sprinkle.loom.ext.skill;

import java.nio.file.Path;
import java.util.List;

/**
 * Skill 元数据与内容记录。
 *
 * @param name        唯一标识符（用于 load_skill 工具参数）
 * @param description 简短描述（注入 system prompt）
 * @param tags        标签列表（辅助 LLM 选择）
 * @param body        完整正文内容（不含 Frontmatter）
 * @param path        SKILL.md 文件路径
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public record SkillEntry(
        String name,
        String description,
        List<String> tags,
        String body,
        Path path
) {
    /**
     * 编程式构造（无文件路径）。
     */
    public SkillEntry(String name, String description, String body) {
        this(name, description, List.of(), body, null);
    }

    /**
     * 带标签的编程式构造。
     */
    public SkillEntry(String name, String description, List<String> tags, String body) {
        this(name, description, tags, body, null);
    }
}
