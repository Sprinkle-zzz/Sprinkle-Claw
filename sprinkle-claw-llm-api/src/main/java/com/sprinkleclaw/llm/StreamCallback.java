package com.sprinkleclaw.llm;

/**
 * LLM 流式回调接口。
 * <p>比简单的 token 回调提供更丰富的事件粒度，覆盖现代 LLM API 的流式事件类型：
 * Anthropic 的 thinking content block、OpenAI 的 tool_calls delta 等。</p>
 *
 * <p>所有方法均有默认空实现，只关心文本 token 的简单场景只需实现 {@link #onToken(String)}。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public interface StreamCallback {

    /**
     * 文本内容 token 增量。
     *
     * @param token 文本片段
     */
    void onToken(String token);

    /**
     * 思考/推理 token 增量（Claude extended thinking）。
     *
     * @param token 思考内容片段
     */
    default void onThinkingToken(String token) {
    }

    /**
     * 工具调用参数片段（流式 tool_use input_json_delta）。
     *
     * @param toolUseId  工具调用 ID
     * @param toolName   工具名称
     * @param inputChunk JSON 参数片段
     */
    default void onToolUseInput(String toolUseId, String toolName, String inputChunk) {
    }

    /**
     * 内容块开始（text / thinking / tool_use）。
     *
     * @param index 内容块索引
     * @param type  内容块类型
     */
    default void onContentBlockStart(int index, String type) {
    }

    /**
     * 内容块结束。
     *
     * @param index 内容块索引
     */
    default void onContentBlockStop(int index) {
    }
}
