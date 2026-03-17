package com.sprinkleclaw.llm;

import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;

import java.util.List;

/**
 * LLM 提供者 SPI 接口。
 * <p>实现类必须无状态且线程安全，因为同一实例可能被多个 AgentLoop 并发使用。</p>
 * <p>作为函数式接口，可以直接用 lambda 表达式创建简单实例：
 * {@code LlmProvider provider = request -> new ChatResponse(...);}
 * 正式实现应覆盖 {@link #providerId()} 返回有意义的标识。</p>
 *
 * @author sprinkle
 * @since 2026/3/17
 */
@FunctionalInterface
public interface LlmProvider {

    /**
     * 提供者唯一标识（如 "anthropic"、"openai"、"ollama"）。
     * <p>默认返回 "custom"，正式实现应覆盖此方法。</p>
     *
     * @return 提供者 ID
     */
    default String providerId() {
        return "custom";
    }

    /**
     * 发送聊天请求并获取同步响应。
     *
     * @param request 聊天请求
     * @return LLM 响应
     * @throws LlmException 当请求失败时抛出（包含可重试标识）
     */
    ChatResponse chat(ChatRequest request) throws LlmException;

    /**
     * 获取此提供者支持的模型列表。
     * <p>默认返回空列表，实现类可覆盖以提供具体模型信息。</p>
     *
     * @return 支持的模型名称列表
     */
    default List<String> supportedModels() {
        return List.of();
    }

    /**
     * 验证 API Key 是否有效（可选实现）。
     * <p>默认返回 true，实现类可覆盖以执行真实验证。</p>
     *
     * @return API Key 有效返回 true
     */
    default boolean validateApiKey() {
        return true;
    }
}
