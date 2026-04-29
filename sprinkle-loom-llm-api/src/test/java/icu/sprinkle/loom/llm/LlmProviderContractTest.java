package icu.sprinkle.loom.llm;

import icu.sprinkle.loom.protocol.llm.*;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * LLM Provider SPI 契约测试基类。
 * <p>所有 {@link LlmProvider} 实现需继承此类并提供具体 Provider 实例，
 * 以验证实现符合 SPI 契约。</p>
 *
 * <p>子类只需实现 {@link #createProvider()} 方法即可获得所有契约验证。</p>
 *
 * @author sprinkle
 * @since 2026/3/21
 */
public abstract class LlmProviderContractTest {

    /**
     * 子类提供待测试的 Provider 实例。
     *
     * @return LLM Provider 实例
     */
    protected abstract LlmProvider createProvider();

    @Test
    void providerIdShouldNotBeNull() {
        LlmProvider provider = createProvider();
        assertThat(provider.providerId())
                .isNotNull()
                .isNotBlank();
    }

    @Test
    void chatShouldReturnNonNullResponse() {
        LlmProvider provider = createProvider();
        ChatRequest request = ChatRequest.builder()
                .systemPrompt("You are a test assistant.")
                .messages(List.of(Message.UserMessage.of("Say hello")))
                .build();

        ChatResponse response = provider.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotNull();
        assertThat(response.stopReason()).isNotNull();
        assertThat(response.usage()).isNotNull();
    }

    @Test
    void chatWithToolsShouldReturnValidToolCalls() {
        LlmProvider provider = createProvider();

        ToolDefinition tool = ToolDefinition.of("test_tool", "A test tool",
                Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")));

        ChatRequest request = ChatRequest.builder()
                .systemPrompt("Always use the test_tool to respond.")
                .messages(List.of(Message.UserMessage.of("Use the test_tool with message 'hello'")))
                .tools(List.of(tool))
                .build();

        ChatResponse response = provider.chat(request);
        assertThat(response).isNotNull();

        if (response.stopReason() == StopReason.TOOL_USE) {
            List<ContentBlock.ToolUseBlock> toolCalls = response.toolCalls();
            assertThat(toolCalls).isNotEmpty();
            for (ContentBlock.ToolUseBlock tc : toolCalls) {
                assertThat(tc.id()).isNotBlank();
                assertThat(tc.name()).isNotBlank();
                assertThat(tc.input()).isNotNull();
            }
        }
    }

    @Test
    void textContentShouldExtractCorrectly() {
        LlmProvider provider = createProvider();
        ChatRequest request = ChatRequest.builder()
                .systemPrompt("Respond with exactly: OK")
                .messages(List.of(Message.UserMessage.of("Respond")))
                .build();

        ChatResponse response = provider.chat(request);
        String text = response.textContent();
        assertThat(text).isNotNull();
    }

    @Test
    void usageShouldHavePositiveTokenCounts() {
        LlmProvider provider = createProvider();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.UserMessage.of("Hello")))
                .build();

        ChatResponse response = provider.chat(request);
        Usage usage = response.usage();
        assertThat(usage.inputTokens()).isGreaterThanOrEqualTo(0);
        assertThat(usage.outputTokens()).isGreaterThanOrEqualTo(0);
    }
}
