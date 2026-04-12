package com.sprinkleclaw.llm.ollama;

import com.sprinkleclaw.llm.LlmConfig;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.llm.LlmProviderFactory;

/**
 * Ollama {@link LlmProviderFactory} 实现，通过 ServiceLoader 自动发现。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class OllamaProviderFactory implements LlmProviderFactory {

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public LlmProvider create(LlmConfig config) {
        return new OllamaProvider(OllamaConfig.from(config));
    }
}
