package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.ToolContext;
import com.sprinkleclaw.tool.ToolProvider;

import java.util.List;

/**
 * 内置工具提供者，通过 {@link java.util.ServiceLoader} 自动发现并注册 4 个内置工具。
 *
 * @see BashTool
 * @see ReadFileTool
 * @see WriteFileTool
 * @see EditFileTool
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class BuiltinToolProvider implements ToolProvider {

    @Override
    public List<AgentTool> provideTools(ToolContext context) {
        return List.of(
                new BashTool(),
                new ReadFileTool(),
                new WriteFileTool(),
                new EditFileTool()
        );
    }
}
