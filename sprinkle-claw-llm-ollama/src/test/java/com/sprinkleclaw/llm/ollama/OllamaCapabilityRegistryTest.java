package com.sprinkleclaw.llm.ollama;

import com.sprinkleclaw.llm.LlmCapabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaCapabilityRegistryTest {

    @Test
    void resolve_knownModel_returnsExactCapabilities() {
        LlmCapabilities caps = OllamaCapabilityRegistry.resolve("llama3.3");
        assertThat(caps.contextWindowTokens()).isEqualTo(128_000);
        assertThat(caps.maxOutputTokens()).isEqualTo(8192);
        assertThat(caps.supportsToolUse()).isTrue();
        assertThat(caps.supportsToolChoice()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void resolve_knownModelWithTag_stripsTagAndResolves() {
        LlmCapabilities caps = OllamaCapabilityRegistry.resolve("llama3.1:8b");
        LlmCapabilities plain = OllamaCapabilityRegistry.resolve("llama3.1");
        assertThat(caps).isEqualTo(plain);
    }

    @Test
    void resolve_unknownModel_returnsConservativeDefaults() {
        LlmCapabilities caps = OllamaCapabilityRegistry.resolve("some-custom-model");
        assertThat(caps.supportsToolUse()).isFalse();
        assertThat(caps.supportsToolChoice()).isFalse();
        assertThat(caps.contextWindowTokens()).isEqualTo(8_192);
        assertThat(caps.maxOutputTokens()).isEqualTo(2048);
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void resolve_modelWithoutToolUse_declaresNoToolSupport() {
        LlmCapabilities caps = OllamaCapabilityRegistry.resolve("mistral");
        assertThat(caps.supportsToolUse()).isFalse();
        assertThat(caps.supportsToolChoice()).isFalse();
        assertThat(caps.contextWindowTokens()).isEqualTo(32_000);
    }

    @Test
    void resolve_qwen_hasToolSupport() {
        LlmCapabilities caps = OllamaCapabilityRegistry.resolve("qwen2.5");
        assertThat(caps.supportsToolUse()).isTrue();
        assertThat(caps.contextWindowTokens()).isEqualTo(128_000);
        assertThat(caps.maxOutputTokens()).isEqualTo(8192);
    }

    @Test
    void resolve_deepseekR1_hasSmallerContext() {
        LlmCapabilities caps = OllamaCapabilityRegistry.resolve("deepseek-r1");
        assertThat(caps.contextWindowTokens()).isEqualTo(64_000);
        assertThat(caps.supportsToolUse()).isTrue();
    }

    @Test
    void stripTag_noColon_returnsOriginal() {
        assertThat(OllamaCapabilityRegistry.stripTag("qwen2.5")).isEqualTo("qwen2.5");
    }

    @Test
    void stripTag_withColon_removesTag() {
        assertThat(OllamaCapabilityRegistry.stripTag("llama3.1:70b-instruct-q4_K_M")).isEqualTo("llama3.1");
    }

    @Test
    void stripTag_nullInput_returnsEmpty() {
        assertThat(OllamaCapabilityRegistry.stripTag(null)).isEmpty();
    }
}
