package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.ToolContext;
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
class WriteFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void write_createsFile() {
        WriteFileTool tool = new WriteFileTool();
        ToolResult result = tool.execute(
                Map.of("path", "new.txt", "content", "hello world"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("Successfully wrote");
        assertThat(tempDir.resolve("new.txt")).hasContent("hello world");
    }

    @Test
    void write_createsParentDirectories() {
        WriteFileTool tool = new WriteFileTool();
        ToolResult result = tool.execute(
                Map.of("path", "sub/dir/file.txt", "content", "nested"),
                new ToolContext(tempDir)
        );

        assertThat(result.isError()).isFalse();
        assertThat(tempDir.resolve("sub/dir/file.txt")).hasContent("nested");
    }

    @Test
    void write_overwritesExisting() throws IOException {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        WriteFileTool tool = new WriteFileTool();
        tool.execute(
                Map.of("path", file.toString(), "content", "new content"),
                new ToolContext(tempDir)
        );

        assertThat(file).hasContent("new content");
    }
}
