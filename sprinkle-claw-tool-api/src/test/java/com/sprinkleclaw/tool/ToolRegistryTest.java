package com.sprinkleclaw.tool;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class ToolRegistryTest {

    @Test
    void register_and_get() {
        ToolRegistry registry = new ToolRegistry();
        AgentTool tool = dummyTool("test_tool");
        registry.register(tool);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.get("test_tool")).isPresent();
        assertThat(registry.get("nonexistent")).isEmpty();
    }

    @Test
    void definitions_returnsAllDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(dummyTool("a"));
        registry.register(dummyTool("b"));

        assertThat(registry.definitions()).hasSize(2);
    }

    private AgentTool dummyTool(String name) {
        return new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.of(name, "desc", Map.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> input, ToolContext context) {
                return ToolResult.success(name, "ok");
            }
        };
    }
}
