package com.sprinkleclaw.ext.skill;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * load_skill 工具：按需加载完整 Skill 内容（Layer 2 加载）。
 *
 * <p>LLM 看到 system prompt 中的 Skill 元数据后，调用此工具获取完整指令。</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class LoadSkillTool implements AgentTool {

    private final SkillLoader skillLoader;

    public LoadSkillTool(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", Map.of(
                "type", "string",
                "description", "The name of the skill to load (as shown in <skills> section of system prompt)."
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("name"));

        return ToolDefinition.of(
                "load_skill",
                "Load the full content of a skill by name. Use this when you need detailed instructions "
                        + "for a specific task. The available skills are listed in your system prompt.",
                schema
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String skillName = (String) input.get("name");
        if (skillName == null || skillName.isBlank()) {
            return ToolResult.error(name(), "Parameter 'name' is required");
        }
        String content = skillLoader.loadSkillContent(skillName);
        return ToolResult.success(name(), content);
    }
}
