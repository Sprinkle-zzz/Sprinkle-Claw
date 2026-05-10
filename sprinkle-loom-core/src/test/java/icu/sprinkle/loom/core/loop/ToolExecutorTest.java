package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.ToolExecution;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.ToolContext;
import icu.sprinkle.loom.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutorTest {

    @Test
    void executionRecordsTruncationMetadata() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.of("large_output", "large output", Map.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> input, ToolContext context) {
                return ToolResult.success("large_output", "0123456789abcdef");
            }
        });
        ToolExecutor executor = new ToolExecutor(
                registry, null, null, new ToolOutputTruncator(2000, 8, null));

        ToolExecutor.ExecutionResult result = executor.executeAll(List.of(
                new ContentBlock.ToolUseBlock("toolu_1", "large_output", Map.of())), new ToolContext(null));

        ToolExecution execution = result.executions().getFirst();
        assertThat(execution.output()).contains("[Output truncated");
        assertThat(execution.truncated()).isTrue();
        assertThat(execution.originalBytes()).isEqualTo(16);
        assertThat(execution.emittedBytes()).isEqualTo(execution.output().getBytes(StandardCharsets.UTF_8).length);
        assertThat(result.results().getFirst().output()).isEqualTo(execution.output());
    }
}
