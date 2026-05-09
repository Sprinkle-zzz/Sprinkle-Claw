package icu.sprinkle.loom.llm;

import icu.sprinkle.loom.api.Stable;
import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.llm.ChatResponse;

import java.util.List;
import java.util.concurrent.Flow;

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
@Stable
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

    /**
     * 获取此提供者的能力声明。
     * <p>默认返回保守的 {@link LlmCapabilities#DEFAULT}，实现类应覆盖以提供准确的能力信息。</p>
     *
     * @return 能力声明
     */
    default LlmCapabilities capabilities() {
        return LlmCapabilities.DEFAULT;
    }

    /**
     * 流式调用 LLM，逐 token 回调。
     * <p>默认实现为非流式 fallback：调用 {@link #chat(ChatRequest)} 后一次性回调全部内容。
     * 支持流式的 Provider 应覆盖此方法以提供真正的流式体验。</p>
     *
     * @param request  聊天请求
     * @param callback 流式回调
     * @return LLM 完整响应（流式结束后组装）
     * @throws LlmException 当请求失败时抛出
     */
    default ChatResponse streamChat(ChatRequest request, StreamCallback callback) {
        ChatResponse response = chat(request);
        String text = response.textContent();
        if (text != null && !text.isEmpty()) {
            callback.onToken(text);
        }
        return response;
    }

    /**
     * 以 Java 标准 {@link Flow.Publisher} 形式流式调用 LLM。
     * <p>默认实现为冷发布者：订阅者首次 {@code request(n)} 后，
     * 在虚拟线程中调用阻塞式 {@link #streamChat(ChatRequest, StreamCallback)}，
     * 并将回调事件转换为 {@link LlmStreamEvent}。</p>
     *
     * @param request 聊天请求
     * @return LLM 流式事件发布者
     */
    default Flow.Publisher<LlmStreamEvent> streamChatPublisher(ChatRequest request) {
        return new BlockingStreamChatPublisher(this, request);
    }
}
