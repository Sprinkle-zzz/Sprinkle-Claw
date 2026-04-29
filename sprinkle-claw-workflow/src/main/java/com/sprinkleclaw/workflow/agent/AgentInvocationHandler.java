package com.sprinkleclaw.workflow.agent;

import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.workflow.agent.structured.GenerateResponseToolMode;
import com.sprinkleclaw.workflow.agent.structured.ParseResult;
import com.sprinkleclaw.workflow.agent.structured.StructuredOutputException;
import com.sprinkleclaw.workflow.agent.structured.StructuredOutputParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 声明式 Agent 动态代理的 {@link InvocationHandler}。
 * <p>拦截接口方法调用，构造 {@link ChatRequest}，调用 LLM，并处理结构化输出解析与自纠正。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class AgentInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentInvocationHandler.class);

    private final Map<Method, AgentMethodMetadata> metadataMap;
    private final AgentFactoryConfig config;
    private final String agentSystemPrompt;

    AgentInvocationHandler(Map<Method, AgentMethodMetadata> metadataMap,
                           AgentFactoryConfig config,
                           String agentSystemPrompt) {
        this.metadataMap = metadataMap;
        this.config = config;
        this.agentSystemPrompt = agentSystemPrompt;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 方法直接处理
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }
        var meta = metadataMap.get(method);
        if (meta == null) {
            throw new UnsupportedOperationException("No metadata for method: " + method.getName());
        }
        return invokeAgent(meta, args);
    }

    private Object invokeAgent(AgentMethodMetadata meta, Object[] args) {
        LlmProvider provider = config.llmProvider();
        boolean supportsToolChoice = provider.capabilities().supportsToolChoice();

        String userMessage = meta.renderUserMessage(args);
        String systemPrompt = meta.renderSystemPrompt(agentSystemPrompt);

        // 非结构化输出（返回 String）：直接调用 LLM
        if (!meta.isStructuredOutput()) {
            ChatRequest request = ChatRequest.builder()
                    .systemPrompt(systemPrompt)
                    .messages(List.of(Message.UserMessage.of(userMessage)))
                    .build();
            ChatResponse response = provider.chat(request);
            return response.textContent();
        }

        // 结构化输出：根据策略决定模式
        StructuredOutputStrategy effective = meta.resolveEffectiveStrategy(supportsToolChoice);

        return switch (effective) {
            case GENERATE_RESPONSE_TOOL -> invokeWithToolMode(meta, userMessage, systemPrompt, provider);
            case PROMPT_JSON -> invokeWithPromptJson(meta, userMessage, systemPrompt, provider);
            case AUTO -> throw new IllegalStateException("AUTO should have been resolved");
        };
    }

    /**
     * GENERATE_RESPONSE_TOOL 模式：通过强制工具调用获取结构化输出。
     */
    private Object invokeWithToolMode(AgentMethodMetadata meta, String userMessage,
                                      String systemPrompt, LlmProvider provider) {
        ChatRequest request = GenerateResponseToolMode.buildRequest(
                userMessage, systemPrompt, meta.returnSchema());
        ChatResponse response = provider.chat(request);
        return GenerateResponseToolMode.extractResult(response, meta.returnType());
    }

    /**
     * PROMPT_JSON 模式：在 system prompt 注入 schema，解析失败时自纠正重试。
     *
     * <p>MVP10 改进：</p>
     * <ul>
     *   <li>重试时 history 不无限累积——只保留 [原始 user, 上轮 assistant, correction]，
     *       避免长 history 浪费 token；schema 已经在 system prompt 中</li>
     *   <li>记录 debug/warn 日志便于调试解析失败原因</li>
     * </ul>
     */
    private Object invokeWithPromptJson(AgentMethodMetadata meta, String userMessage,
                                        String systemPrompt, LlmProvider provider) {
        var parser = new StructuredOutputParser<>(meta.returnType(), meta.returnSchema());
        Message originalUser = Message.UserMessage.of(userMessage);

        // 首次调用
        ChatRequest request = ChatRequest.builder()
                .systemPrompt(systemPrompt)
                .messages(List.of(originalUser))
                .build();
        ChatResponse response = provider.chat(request);

        for (int attempt = 0; attempt <= meta.maxCorrectionRetries(); attempt++) {
            String textToParse = response.textContent();
            ParseResult<?> result = parser.parse(textToParse);

            switch (result) {
                case ParseResult.Success<?> s -> {
                    if (attempt > 0) {
                        log.debug("[StructuredOutput] 自纠正成功（第 {} 次重试后）", attempt);
                    }
                    return s.value();
                }
                case ParseResult.Fatal<?> f -> {
                    log.error("[StructuredOutput] 解析致命错误: {}", f.error());
                    throw new StructuredOutputException(f.error());
                }
                case ParseResult.Retry<?> r -> {
                    if (attempt == meta.maxCorrectionRetries()) {
                        log.error("[StructuredOutput] 重试次数耗尽（{}/{} 次），最后错误: {}",
                                attempt, meta.maxCorrectionRetries(), r.correctionPrompt());
                        throw new StructuredOutputException(
                                "Max correction retries exceeded. Last correction: " + r.correctionPrompt());
                    }
                    log.warn("[StructuredOutput] 第 {} 次解析失败，发送 correction（{}）",
                            attempt + 1, summarize(r.correctionPrompt()));

                    // history 仅保留 [原始 user, 上轮 assistant, correction]——schema 在 system 中无需重复
                    var assistantTurn = new Message.AssistantMessage(
                            List.of(new ContentBlock.TextBlock(textToParse)),
                            response.stopReason());
                    var correction = Message.UserMessage.of(r.correctionPrompt());

                    ChatRequest retryRequest = ChatRequest.builder()
                            .systemPrompt(systemPrompt)
                            .messages(List.of(originalUser, assistantTurn, correction))
                            .build();
                    response = provider.chat(retryRequest);
                }
            }
        }
        throw new StructuredOutputException("Unreachable: correction loop exited without result");
    }

    private static String summarize(String s) {
        if (s == null) {
            return "<null>";
        }
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "...";
    }

    private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "AgentProxy[" + proxy.getClass().getInterfaces()[0].getSimpleName() + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
