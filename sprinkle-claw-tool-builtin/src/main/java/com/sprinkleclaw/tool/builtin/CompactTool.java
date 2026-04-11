package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.RiskLevel;
import com.sprinkleclaw.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手动上下文压缩工具，让模型可以主动触发上下文压缩。
 *
 * <p>适用于模型判断当前上下文已经"够用"时，主动释放 token 空间。
 * 工具执行时只是在 attributes 中设置标记，实际压缩由 {@code ContextManager}
 * 在下一轮 LLM 调用前执行。</p>
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public final class CompactTool implements AgentTool {

    /**
     * 在 ToolContext.attributes 中标记手动压缩请求的键。
     */
    public static final String COMPACT_REQUESTED_KEY = "_compact_requested";

    /**
     * 在 ToolContext.attributes 中传递压缩焦点的键。
     */
    public static final String COMPACT_FOCUS_KEY = "_compact_focus";

    @Override
    public ToolDefinition definition() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("focus", Map.of("type", "string",
                "description", "可选的压缩焦点提示，指导摘要重点关注的内容"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        return ToolDefinition.of("compact",
                ToolDescriptions.load("compact",
                        "Compress the conversation context to free up token space."),
                schema);
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String focus = (String) input.get("focus");

        context.setAttribute(COMPACT_REQUESTED_KEY, true);
        if (focus != null && !focus.isBlank()) {
            context.setAttribute(COMPACT_FOCUS_KEY, focus);
        }

        return ToolResult.success(name(),
                "Compression scheduled. It will be executed before the next LLM call.");
    }

    @Override
    public RiskLevel riskLevel() { return RiskLevel.LOW; }
}
