package com.sprinkleclaw.llm.anthropic;

import com.sprinkleclaw.llm.LlmConfig;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.llm.LlmProviderFactory;

/**
 * Anthropic 提供者工厂，通过 {@link java.util.ServiceLoader} 自动注册。
 *
 * @author sprinkle
 * @see AnthropicProvider
 * @since 2026/3/18
 */
public final class AnthropicProviderFactory implements LlmProviderFactory {

    @Override
    public String providerId() {
        return "anthropic";
    }

    /**
     * 创建 {@link AnthropicProvider} 实例。
     *
     * @param config LLM 配置
     * @return Anthropic 提供者
     */
    @Override
    public LlmProvider create(LlmConfig config) {
        return new AnthropicProvider(config);
    }
}
