package com.sprinkleclaw.ext.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SkillLoader + SkillFrontmatterParser 测试。
 *
 * @author sprinkle
 * @since 2026/4/4
 */
class SkillLoaderTest {

    @TempDir
    Path tempDir;

    private void createSkill(String name, String content) throws IOException {
        Path skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }

    @Test
    void scanDirectory_loadsSkillWithFrontmatter() throws IOException {
        createSkill("code-review", """
                ---
                name: code-review
                description: Review code for quality and security
                tags: code, review, quality
                ---

                # Code Review Skill

                Step 1: Read the file...
                Step 2: Check for issues...
                """);

        SkillLoader loader = new SkillLoader();
        loader.scanDirectory(tempDir);

        assertThat(loader.registry().size()).isEqualTo(1);

        var skill = loader.registry().find("code-review");
        assertThat(skill).isPresent();
        assertThat(skill.get().description()).isEqualTo("Review code for quality and security");
        assertThat(skill.get().tags()).containsExactly("code", "review", "quality");
        assertThat(skill.get().body()).contains("Step 1: Read the file");
    }

    @Test
    void scanDirectory_multipleSkills() throws IOException {
        createSkill("skill-a", """
                ---
                name: skill-a
                description: First skill
                ---
                Body A
                """);
        createSkill("skill-b", """
                ---
                name: skill-b
                description: Second skill
                ---
                Body B
                """);

        SkillLoader loader = new SkillLoader();
        loader.scanDirectory(tempDir);

        assertThat(loader.registry().size()).isEqualTo(2);
    }

    @Test
    void scanDirectory_nonExistentDir_noError() {
        SkillLoader loader = new SkillLoader();
        loader.scanDirectory(tempDir.resolve("nonexistent"));
        assertThat(loader.registry().size()).isZero();
    }

    @Test
    void loadSkillContent_returnsWrappedContent() throws IOException {
        createSkill("test-skill", """
                ---
                name: test-skill
                description: A test skill
                ---
                Full skill content here.
                """);

        SkillLoader loader = new SkillLoader();
        loader.scanDirectory(tempDir);

        String content = loader.loadSkillContent("test-skill");
        assertThat(content).contains("<skill name=\"test-skill\">");
        assertThat(content).contains("Full skill content here.");
    }

    @Test
    void loadSkillContent_unknownSkill_returnsError() {
        SkillLoader loader = new SkillLoader();
        String result = loader.loadSkillContent("nonexistent");
        assertThat(result).contains("Error: Unknown skill");
    }

    @Test
    void getSystemPromptSection_empty_returnsEmptyString() {
        SkillLoader loader = new SkillLoader();
        assertThat(loader.getSystemPromptSection()).isEmpty();
    }

    @Test
    void getSystemPromptSection_withSkills_containsMetadata() throws IOException {
        createSkill("my-skill", """
                ---
                name: my-skill
                description: Does something useful
                tags: util
                ---
                Content
                """);

        SkillLoader loader = new SkillLoader();
        loader.scanDirectory(tempDir);

        String section = loader.getSystemPromptSection();
        assertThat(section).contains("my-skill");
        assertThat(section).contains("Does something useful");
        assertThat(section).contains("load_skill");
    }

    @Test
    void frontmatterParser_missingDelimiter_throws() {
        assertThatThrownBy(() -> SkillFrontmatterParser.parse("no frontmatter here"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frontmatterParser_missingRequiredField_throws() {
        var result = SkillFrontmatterParser.parse("""
                ---
                description: has desc but no name
                ---
                body
                """);
        assertThatThrownBy(() -> result.require("name"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
