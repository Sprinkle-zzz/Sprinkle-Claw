package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDefinitionSorterTest {

    private ToolDefinitionSorter sorter;

    @BeforeEach
    void setUp() {
        sorter = new ToolDefinitionSorter();
    }

    private static ToolDefinition tool(String name) {
        return ToolDefinition.of(name, "desc", Map.of());
    }

    @Test
    void builtinToolsBeforeExternal() {
        var tools = List.of(tool("custom_b"), tool("bash"), tool("custom_a"), tool("read_file"));
        var sorted = sorter.sort(tools);

        assertThat(sorted.stream().map(ToolDefinition::name).toList())
                .containsExactly("bash", "read_file", "custom_a", "custom_b");
    }

    @Test
    void builtinToolsSortedByName() {
        var tools = List.of(tool("write_file"), tool("bash"), tool("edit_file"), tool("read_file"));
        var sorted = sorter.sort(tools);

        assertThat(sorted.stream().map(ToolDefinition::name).toList())
                .containsExactly("bash", "edit_file", "read_file", "write_file");
    }

    @Test
    void externalToolsSortedByName() {
        var tools = List.of(tool("z_tool"), tool("a_tool"), tool("m_tool"));
        var sorted = sorter.sort(tools);

        assertThat(sorted.stream().map(ToolDefinition::name).toList())
                .containsExactly("a_tool", "m_tool", "z_tool");
    }

    @Test
    void stableSortingAcrossMultipleCalls() {
        var tools = List.of(tool("custom_x"), tool("bash"), tool("grep"), tool("custom_a"));
        var first = sorter.sort(tools);
        var second = sorter.sort(tools);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void emptyListReturnsEmpty() {
        assertThat(sorter.sort(List.of())).isEmpty();
    }

    @Test
    void singleToolReturnsSame() {
        var tools = List.of(tool("bash"));
        assertThat(sorter.sort(tools)).hasSize(1);
    }

    @Test
    void nullReturnsEmpty() {
        assertThat(sorter.sort(null)).isEmpty();
    }

    @Test
    void resultIsUnmodifiable() {
        var tools = List.of(tool("bash"), tool("custom"));
        var sorted = sorter.sort(tools);
        assertThat(sorted).isUnmodifiable();
    }

    @Test
    void isBuiltinRecognizesKnownTools() {
        assertThat(ToolDefinitionSorter.isBuiltin("bash")).isTrue();
        assertThat(ToolDefinitionSorter.isBuiltin("read_file")).isTrue();
        assertThat(ToolDefinitionSorter.isBuiltin("task_create")).isTrue();
        assertThat(ToolDefinitionSorter.isBuiltin("sub_agent")).isTrue();
        assertThat(ToolDefinitionSorter.isBuiltin("custom_tool")).isFalse();
    }
}
