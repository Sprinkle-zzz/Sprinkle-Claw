package icu.sprinkle.loom.llm;

import icu.sprinkle.loom.protocol.llm.ChatResponse;

import java.time.Instant;

/**
 * LLM 流式调用过程中对外发布的标准事件。
 *
 * <p>错误通过 {@link java.util.concurrent.Flow.Subscriber#onError(Throwable)}
 * 传播；{@link Complete} 是携带最终响应的业务事件。</p>
 *
 * @author sprinkle
 * @since 2026/5/10
 */
public sealed interface LlmStreamEvent {

    /**
     * 事件产生时间。
     */
    Instant timestamp();

    /**
     * 文本 token 增量。
     */
    record Token(Instant timestamp, String token) implements LlmStreamEvent {
    }

    /**
     * thinking token 增量。
     */
    record ThinkingToken(Instant timestamp, String token) implements LlmStreamEvent {
    }

    /**
     * 工具调用参数增量。
     */
    record ToolInputChunk(Instant timestamp, String toolUseId, String toolName,
                          String inputChunk) implements LlmStreamEvent {
    }

    /**
     * 内容块开始。
     */
    record ContentBlockStart(Instant timestamp, int index, String type) implements LlmStreamEvent {
    }

    /**
     * 内容块结束。
     */
    record ContentBlockStop(Instant timestamp, int index) implements LlmStreamEvent {
    }

    /**
     * 流式调用业务完成，携带最终聚合响应。
     */
    record Complete(Instant timestamp, ChatResponse response) implements LlmStreamEvent {
    }
}
