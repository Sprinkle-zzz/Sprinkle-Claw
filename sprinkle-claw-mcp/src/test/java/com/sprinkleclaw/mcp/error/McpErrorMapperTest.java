package com.sprinkleclaw.mcp.error;

import com.sprinkleclaw.protocol.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpErrorMapperTest {

    @Test
    void should_wrap_exception_class_and_message() {
        ToolResult r = McpErrorMapper.toToolResult("read", new IllegalStateException("not init"));
        assertThat(r.isError()).isTrue();
        assertThat(r.toolName()).isEqualTo("read");
        assertThat(r.output()).contains("MCP error").contains("IllegalStateException").contains("not init");
    }

    @Test
    void should_handle_null_message() {
        ToolResult r = McpErrorMapper.toToolResult("x", new RuntimeException());
        assertThat(r.isError()).isTrue();
        assertThat(r.output()).contains("RuntimeException");
    }
}
