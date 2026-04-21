package com.sprinkleclaw.llm.ollama;

import com.sprinkleclaw.llm.LlmCapabilities;

import java.util.Map;
import java.util.Set;

/**
 * 按模型名推断 Ollama 模型的能力声明。
 * <p>已知模型查表返回精确能力；未知模型返回保守默认值（不声明 tool_use 支持）。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class OllamaCapabilityRegistry {

    private static final Set<String> VISION_MODELS = Set.of(
            "llava", "bakllava", "moondream", "qwen2-vl"
    );

    private static final Map<String, LlmCapabilities> KNOWN = Map.ofEntries(
            Map.entry("llama3.3", caps(128_000, 8192, true, false)),
            Map.entry("llama3.1", caps(128_000, 4096, true, false)),
            Map.entry("llama3.2", caps(128_000, 4096, true, false)),
            Map.entry("qwen2.5", caps(128_000, 8192, true, false)),
            Map.entry("qwen3", caps(128_000, 8192, true, false)),
            Map.entry("deepseek-r1", caps(64_000, 8192, true, false)),
            Map.entry("mistral", caps(32_000, 4096, false, false)),
            Map.entry("gemma2", caps(8_192, 4096, false, false)),
            Map.entry("phi3", caps(128_000, 4096, false, false)),
            Map.entry("codellama", caps(16_000, 4096, false, false)),
            Map.entry("llava", caps(4_096, 2048, false, true)),
            Map.entry("bakllava", caps(4_096, 2048, false, true)),
            Map.entry("moondream", caps(4_096, 2048, false, true)),
            Map.entry("qwen2-vl", caps(32_000, 4096, false, true))
    );

    /**
     * 解析模型能力。{@code model} 可含 tag（如 "llama3.1:8b"），会自动归一化。
     */
    public static LlmCapabilities resolve(String model) {
        String key = stripTag(model);
        return KNOWN.getOrDefault(key, conservative());
    }

    /**
     * 判断模型是否支持 Vision（图片输入）。
     */
    public static boolean supportsVision(String model) {
        String key = stripTag(model);
        return VISION_MODELS.contains(key);
    }

    /**
     * 保守回退：未知模型不声明 tool_use 支持，触发 prompt-based 降级。
     */
    private static LlmCapabilities conservative() {
        return LlmCapabilities.builder()
                .supportsReasoning(false)
                .supportsStructuredOutput(false)
                .contextWindowTokens(8_192)
                .maxOutputTokens(2048)
                .supportsToolChoice(false)
                .supportsStreaming(true)
                .supportsToolUse(false)
                .supportsPromptCache(false)
                .supportsVision(false)
                .supportsPdfInput(false)
                .supportsAudioInput(false)
                .build();
    }

    private static LlmCapabilities caps(int ctx, int maxOut, boolean toolUse, boolean vision) {
        return LlmCapabilities.builder()
                .supportsReasoning(false)
                .supportsStructuredOutput(false)
                .contextWindowTokens(ctx)
                .maxOutputTokens(maxOut)
                .supportsToolChoice(toolUse)
                .supportsStreaming(true)
                .supportsToolUse(toolUse)
                .supportsPromptCache(false)
                .supportsVision(vision)
                .supportsPdfInput(false)
                .supportsAudioInput(false)
                .build();
    }

    /**
     * "llama3.1:8b" → "llama3.1"，"qwen2.5" → "qwen2.5"
     */
    static String stripTag(String model) {
        if (model == null) return "";
        int colon = model.indexOf(':');
        return colon > 0 ? model.substring(0, colon) : model;
    }
}
