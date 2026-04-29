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
class ReadFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void read_wholeFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3");

        ReadFileTool tool = new ReadFileTool();
        ToolResult result = tool.execute(Map.of("path", file.toString()), new ToolContext(tempDir));

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("line1");
        assertThat(result.output()).contains("line3");
    }

    @Test
    void read_withOffsetAndLimit() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "a\nb\nc\nd\ne");

        ReadFileTool tool = new ReadFileTool();
        ToolResult result = tool.execute(
                Map.of("path", file.toString(), "offset", 2, "limit", 2),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("b");
        assertThat(result.output()).contains("c");
        assertThat(result.output()).doesNotContain("a");
        assertThat(result.output()).doesNotContain("d");
    }

    @Test
    void read_fileNotFound() {
        ReadFileTool tool = new ReadFileTool();
        ToolResult result = tool.execute(
                Map.of("path", "nonexistent.txt"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("not found");
    }
}
