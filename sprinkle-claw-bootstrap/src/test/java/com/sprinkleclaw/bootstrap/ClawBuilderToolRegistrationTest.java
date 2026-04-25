package com.sprinkleclaw.bootstrap;

import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.llm.Usage;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ClawBuilderToolRegistrationTest {

    private static LlmProvider mockProvider() {
        return request -> new ChatResponse(
                List.of(new ContentBlock.TextBlock("ok")),
                StopReason.END_TURN,
                new Usage(10, 20),
                "test-model"
        );
    }

    private static Set<String> toolNames(AgentContext ctx) {
        return ctx.toolDefinitions().stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toSet());
    }

    @Test
    void defaultBuild_registersNoBuiltinTools() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).doesNotContain("read_file", "write_file", "edit_file", "bash");
        claw.close();
    }

    @Test
    void enableFileTools_registersReadWriteEdit() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .enableFileTools()
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("read_file", "write_file", "edit_file");
        assertThat(names).doesNotContain("bash");
        claw.close();
    }

    @Test
    void enableBashTool_registersOnlyBash() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .enableBashTool()
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("bash");
        assertThat(names).doesNotContain("read_file", "write_file", "edit_file");
        claw.close();
    }

    @Test
    void enableCodingTools_registersAllCodingTools() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .enableCodingTools()
                .compactionThreshold(100_000)
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("read_file", "write_file", "edit_file", "bash", "todo_write", "compact");
        claw.close();
    }

    @Test
    void enableManualCompact_respectsThreshold() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .enableManualCompact()
                .compactionThreshold(50_000)
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("compact");
        claw.close();
    }

    @Test
    void manualCompactWithoutThreshold_notRegistered() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .enableManualCompact()
                .compactionThreshold(0)
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).doesNotContain("compact");
        claw.close();
    }

    @Test
    void customTools_registeredAlongsideBuiltin() {
        var customTool = new com.sprinkleclaw.tool.AgentTool() {
            @Override
            public com.sprinkleclaw.protocol.tool.ToolDefinition definition() {
                return com.sprinkleclaw.protocol.tool.ToolDefinition.of("my_custom_tool", "Custom", java.util.Map.of());
            }

            @Override
            public com.sprinkleclaw.protocol.tool.ToolResult execute(
                    java.util.Map<String, Object> input, com.sprinkleclaw.tool.ToolContext ctx) {
                return com.sprinkleclaw.protocol.tool.ToolResult.success("ok", "done");
            }
        };

        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .enableFileTools()
                .addTool(customTool)
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("read_file", "write_file", "edit_file", "my_custom_tool");
        claw.close();
    }

    @Test
    void noToolsRegistered_byDefault() {
        var claw = ClawBuilder.create()
                .llmProvider(mockProvider())
                .build();

        assertThat(claw.context().toolDefinitions()).isEmpty();
        claw.close();
    }
}
