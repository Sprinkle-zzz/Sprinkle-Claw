package icu.sprinkle.loom.bootstrap;

import icu.sprinkle.loom.core.AgentResult;
import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LoomBuilderToolRegistrationTest {

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
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).doesNotContain("read_file", "write_file", "edit_file", "bash");
        claw.close();
    }

    @Test
    void enableFileTools_registersReadWriteEdit() {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .workingDirectory(Path.of("."))
                .enableFileTools()
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("read_file", "write_file", "edit_file");
        assertThat(names).doesNotContain("bash");
        claw.close();
    }

    @Test
    void enableBashTool_registersOnlyBash() {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .workingDirectory(Path.of("."))
                .enableBashTool()
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("bash");
        assertThat(names).doesNotContain("read_file", "write_file", "edit_file");
        claw.close();
    }

    @Test
    void enableCodingTools_registersAllCodingTools() {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .workingDirectory(Path.of("."))
                .enableCodingTools()
                .compactionThreshold(100_000)
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("read_file", "write_file", "edit_file", "bash", "todo_write", "compact");
        claw.close();
    }

    @Test
    void enableManualCompact_respectsThreshold() {
        var claw = LoomBuilder.create()
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
        var claw = LoomBuilder.create()
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
        var customTool = new icu.sprinkle.loom.tool.AgentTool() {
            @Override
            public icu.sprinkle.loom.protocol.tool.ToolDefinition definition() {
                return icu.sprinkle.loom.protocol.tool.ToolDefinition.of("my_custom_tool", "Custom", java.util.Map.of());
            }

            @Override
            public icu.sprinkle.loom.protocol.tool.ToolResult execute(
                    java.util.Map<String, Object> input, icu.sprinkle.loom.tool.ToolContext ctx) {
                return icu.sprinkle.loom.protocol.tool.ToolResult.success("ok", "done");
            }
        };

        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .workingDirectory(Path.of("."))
                .enableFileTools()
                .addTool(customTool)
                .build();

        Set<String> names = toolNames(claw.context());
        assertThat(names).contains("read_file", "write_file", "edit_file", "my_custom_tool");
        claw.close();
    }

    @Test
    void noToolsRegistered_byDefault() {
        var claw = LoomBuilder.create()
                .llmProvider(mockProvider())
                .build();

        assertThat(claw.context().toolDefinitions()).isEmpty();
        claw.close();
    }
}
