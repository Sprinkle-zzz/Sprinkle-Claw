package icu.sprinkle.loom.ext.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Skill 两层加载器。
 *
 * <ul>
 *   <li>Layer 1：扫描目录，将 Skill 元数据注入 system prompt（~100 tokens/skill）</li>
 *   <li>Layer 2：LLM 调用 {@code load_skill} 时，返回完整 Skill 内容</li>
 * </ul>
 *
 * <p>目录结构约定：{@code skills/{name}/SKILL.md}</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private static final String SKILL_FILE = "SKILL.md";
    static final int MAX_SKILLS_DEFAULT = 50;

    private final SkillRegistry registry;
    private final int maxSkills;

    public SkillLoader() {
        this(MAX_SKILLS_DEFAULT);
    }

    public SkillLoader(int maxSkills) {
        this.registry = new SkillRegistry();
        this.maxSkills = maxSkills;
    }

    /**
     * 扫描指定目录，加载所有 Skill 的元数据和内容。
     *
     * @param skillsDir skills 根目录（每个子目录包含 SKILL.md）
     */
    public void scanDirectory(Path skillsDir) {
        if (!Files.isDirectory(skillsDir)) {
            log.warn("Skills directory does not exist or is not a directory: {}", skillsDir);
            return;
        }

        try (var dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory)
                    .limit(maxSkills)
                    .forEach(dir -> {
                        Path skillFile = dir.resolve(SKILL_FILE);
                        if (Files.isRegularFile(skillFile)) {
                            loadSkill(skillFile);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan skills directory: {}", skillsDir, e);
        }

        log.info("Loaded {} skill(s) from {}", registry.size(), skillsDir);
    }

    private void loadSkill(Path skillFile) {
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            SkillFrontmatterParser.ParseResult parsed = SkillFrontmatterParser.parse(content);

            String name = parsed.require("name");
            String description = parsed.require("description");
            List<String> tags = parsed.getTags("tags");
            String body = parsed.body();

            SkillEntry entry = new SkillEntry(name, description, tags, body, skillFile);
            registry.register(entry);
            log.debug("Loaded skill '{}': {}", name, description);
        } catch (Exception e) {
            log.warn("Failed to load skill from {}: {}", skillFile, e.getMessage());
        }
    }

    /**
     * Layer 1：生成注入 system prompt 的 Skill 元数据段落。
     *
     * @return system prompt 中的 Skill 元数据 XML 段落，无 Skill 时返回空字符串
     */
    public String getSystemPromptSection() {
        if (registry.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        sb.append("\n## Available Skills\n\n");
        sb.append("Use the `load_skill` tool to load the full content of a skill when needed.\n\n");
        sb.append("<skills>\n");

        for (SkillEntry entry : registry.all()) {
            sb.append("  <skill name=\"").append(entry.name()).append("\">");
            sb.append(entry.description());
            if (!entry.tags().isEmpty()) {
                sb.append(" [").append(String.join(", ", entry.tags())).append("]");
            }
            sb.append("</skill>\n");
        }

        sb.append("</skills>\n");
        return sb.toString();
    }

    /**
     * Layer 2：根据名称加载完整 Skill 内容。
     *
     * @param name Skill 名称
     * @return 包含完整 Skill 内容的 XML 包装字符串；找不到时返回错误消息
     */
    public String loadSkillContent(String name) {
        return registry.find(name)
                .map(entry -> "<skill name=\"" + name + "\">\n" + entry.body() + "\n</skill>")
                .orElse("Error: Unknown skill '" + name + "'. Available skills: " + registry.names());
    }

    /**
     * 获取 Skill 注册表。
     */
    public SkillRegistry registry() {
        return registry;
    }
}
