package com.sprinkleclaw.llm;

/**
 * LLM 提供者工厂 SPI，通过 {@link java.util.ServiceLoader} 自动发现。
 * <p>每个实现负责根据 {@link LlmConfig} 创建对应的 {@link LlmProvider} 实例。</p>
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public interface LlmProviderFactory {

    /**
     * 提供者唯一标识，必须与对应 {@link LlmProvider#providerId()} 一致。
     *
     * @return 提供者 ID
     */
    String providerId();

    /**
     * 根据配置创建 LLM 提供者实例。
     *
     * @param config LLM 配置
     * @return 提供者实例
     */
    LlmProvider create(LlmConfig config);
}
