package icu.sprinkle.loom.llm.cache;

import icu.sprinkle.loom.llm.LlmCapabilities;
import icu.sprinkle.loom.protocol.llm.ChatRequest;

/**
 * Prompt 缓存策略 SPI。
 * <p>在 LLM 调用前修饰 {@link ChatRequest}，给需要缓存的 ContentBlock 打 CacheControl 标记。</p>
 *
 * @author sprinkle
 * @since 0.8.0 (MVP7)
 */
@FunctionalInterface
public interface CachePolicy {

    /**
     * 根据能力声明修饰请求，返回可能带有 CacheControl 标记的新请求。
     *
     * @param request      原始请求
     * @param capabilities Provider 能力声明
     * @return 修饰后的请求（可以是原 request 原样返回）
     */
    ChatRequest decide(ChatRequest request, LlmCapabilities capabilities);

    default String policyId() {
        return "custom";
    }

    CachePolicy NOOP = (req, cap) -> req;
}
