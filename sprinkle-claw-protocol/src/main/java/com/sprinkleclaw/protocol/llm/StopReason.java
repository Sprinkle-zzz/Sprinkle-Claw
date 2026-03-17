package com.sprinkleclaw.protocol.llm;

/**
 * LLM 停止生成的原因枚举。
 * 统一映射 Anthropic 的 stop_reason 和 OpenAI 的 finish_reason。
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public enum StopReason {
    /**
     * 正常结束（Anthropic: end_turn, OpenAI: stop）
     */
    END_TURN,
    /**
     * 需要调用工具（Anthropic: tool_use, OpenAI: tool_calls）
     */
    TOOL_USE,
    /**
     * 达到最大 token 限制（Anthropic: max_tokens, OpenAI: length）
     */
    MAX_TOKENS,
    /**
     * 命中停止序列（Anthropic: stop_sequence）
     */
    STOP_SEQUENCE,
    /**
     * 内容过滤（仅 OpenAI）
     */
    CONTENT_FILTER
}
