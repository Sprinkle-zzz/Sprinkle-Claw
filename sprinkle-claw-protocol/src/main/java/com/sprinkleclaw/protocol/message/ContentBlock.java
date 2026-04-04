package com.sprinkleclaw.protocol.message;

import java.util.Map;

/**
 * LLM 响应中的内容块。
 * 统一封装 Anthropic（text/tool_use/thinking）和 OpenAI（text/tool_calls）的内容格式。
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public sealed interface ContentBlock {

    /**
     * 纯文本内容块。
     *
     * @param text 文本内容
     */
    record TextBlock(String text) implements ContentBlock {
    }

    /**
     * 工具调用请求块，表示 LLM 希望调用某个工具。
     *
     * @param id    工具调用的唯一标识，用于关联调用结果
     * @param name  要调用的工具名称
     * @param input 工具输入参数（键值对）
     */
    record ToolUseBlock(String id, String name, Map<String, Object> input) implements ContentBlock {
    }

    /**
     * LLM 内部推理内容块。
     * <p>Anthropic 以 {@code thinking} content block 返回；OpenAI 推理模型通过 {@code reasoning_tokens} 计量，
     * 推理内容不直接暴露给用户，但可通过 {@link Usage#reasoningTokens()} 获取推理 token 消耗。</p>
     *
     * @param thinking 推理/思考文本
     */
    record ThinkingBlock(String thinking) implements ContentBlock {
    }
}
