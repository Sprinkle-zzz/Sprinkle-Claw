package icu.sprinkle.loom.tool.builtin;

import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class EditFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void edit_replacesUniqueString() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World\nGoodbye World");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "Hello World",
                        "new_string", "Hi World"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("Hi World\nGoodbye World");
    }

    @Test
    void edit_failsOnDuplicate() throws IOException {
        Path file = tempDir.resolve("dup.txt");
        Files.writeString(file, "abc\nabc");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "abc",
                        "new_string", "xyz"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("multiple times");
    }

    @Test
    void edit_failsOnNotFound() throws IOException {
        Path file = tempDir.resolve("miss.txt");
        Files.writeString(file, "hello");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "nonexistent",
                        "new_string", "xyz"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("not found");
    }

    @Test
    void edit_lineTrimmedFallback() throws IOException {
        Path file = tempDir.resolve("trim.txt");
        Files.writeString(file, "  hello  \n  world  ");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "hello\nworld",
                        "new_string", "  hi  \n  earth  "),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("  hi  \n  earth  ");
    }

    @Test
    void edit_indentationFlexibleFallback() throws IOException {
        Path file = tempDir.resolve("indent.txt");
        Files.writeString(file, "    line1\n    line2\n    line3");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "line1\nline2\nline3",
                        "new_string", "    newLine1\n    newLine2\n    newLine3"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
    }

    @Test
    void edit_replaceAll() throws IOException {
        Path file = tempDir.resolve("all.txt");
        Files.writeString(file, "foo bar foo baz foo");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "foo",
                        "new_string", "qux",
                        "replace_all", true),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("qux bar qux baz qux");
    }

    @Test
    void edit_identicalStringsRejected() throws IOException {
        Path file = tempDir.resolve("same.txt");
        Files.writeString(file, "hello");

        EditFileTool tool = new EditFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(),
                        "old_string", "hello",
                        "new_string", "hello"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("identical");
    }
}
