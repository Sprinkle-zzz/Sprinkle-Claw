package icu.sprinkle.loom.llm.openai;

import icu.sprinkle.loom.llm.LlmConfig;
import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.llm.LlmProviderFactory;

/**
 * OpenAI 兼容提供者工厂，通过 {@link java.util.ServiceLoader} 自动注册。
 * <p>一个工厂覆盖所有 OpenAI 兼容 API 的厂商（OpenAI、DeepSeek、Qwen、GLM 等）。</p>
 *
 * @see OpenAiCompatibleProvider
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class OpenAiCompatibleProviderFactory implements LlmProviderFactory {

    @Override
    public String providerId() {
        return "openai";
    }

    /**
     * 创建 OpenAI 兼容提供者实例。
     *
     * @param config LLM 配置（通过 baseUrl 区分不同厂商）
     * @return 提供者实例
     */
    @Override
    public LlmProvider create(LlmConfig config) {
        return new OpenAiCompatibleProvider(config);
    }
}
