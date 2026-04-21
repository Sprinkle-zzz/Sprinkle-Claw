package com.sprinkleclaw.llm.cache;

import com.sprinkleclaw.llm.LlmCapabilities;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.message.CacheControl;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.ContentBlock.TextBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;

import java.util.List;

/**
 * 基于 {@link CacheStrategy} 的默认缓存策略实现。
 *
 * @author sprinkle
 * @since 0.8.0 (MVP7)
 */
public final class DefaultCachePolicy implements CachePolicy {

    private static final int SYSTEM_PROMPT_MIN_LENGTH = 1024;

    private final CacheStrategy strategy;

    public DefaultCachePolicy(CacheStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public String policyId() {
        return "default-" + strategy.name().toLowerCase();
    }

    @Override
    public ChatRequest decide(ChatRequest request, LlmCapabilities capabilities) {
        if (!capabilities.supportsPromptCache()) {
            return request;
        }

        return switch (strategy) {
            case MANUAL -> request;
            case AUTO_SYSTEM_PROMPT -> markSystemPrompt(request);
            case AUTO_SYSTEM_AND_TOOLS -> markSystemAndTools(request);
            case AUTO_AGGRESSIVE -> markAggressive(request);
        };
    }

    private ChatRequest markSystemPrompt(ChatRequest request) {
        String sp = request.systemPrompt();
        if (sp == null || sp.length() < SYSTEM_PROMPT_MIN_LENGTH) {
            return request;
        }
        return request.toBuilder()
                .systemCacheControl(CacheControl.ephemeral())
                .build();
    }

    private ChatRequest markSystemAndTools(ChatRequest request) {
        ChatRequest.Builder builder = request.toBuilder();

        String sp = request.systemPrompt();
        if (sp != null && sp.length() >= SYSTEM_PROMPT_MIN_LENGTH) {
            builder.systemCacheControl(CacheControl.ephemeral());
        }

        builder.toolsCacheControl(CacheControl.ephemeral());

        return builder.build();
    }

    private ChatRequest markAggressive(ChatRequest request) {
        ChatRequest marked = markSystemAndTools(request);
        // AUTO_AGGRESSIVE 模式保留扩展空间，当前与 AUTO_SYSTEM_AND_TOOLS 行为一致
        return marked;
    }
}
