package com.sprinkleclaw.tool;

import java.util.List;

/**
 * 工具提供者 SPI，用于在运行时动态提供工具。
 * <p>通过 {@link java.util.ServiceLoader} 自动发现，或由 {@code ClawBuilder} 手动注册。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public interface ToolProvider {

    /**
     * 根据上下文提供可用工具列表。
     *
     * @param context 工具上下文
     * @return 工具列表
     */
    List<AgentTool> provideTools(ToolContext context);
}
