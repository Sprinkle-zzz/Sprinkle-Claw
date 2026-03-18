package com.sprinkleclaw.llm.openai;

import com.sprinkleclaw.llm.LlmConfig;
import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.protocol.llm.ChatRequest;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.llm.StopReason;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeek 模型可用性测试。
 * <p>需要环境变量 {@code DEEPSEEK_API_KEY}，未设置时自动跳过。</p>
 *
 * <p>运行方式：</p>
 * <pre>
 * set DEEPSEEK_API_KEY=sk-xxx
 * mvn test -pl sprinkle-claw-llm-openai -Dtest=DeepSeekProviderTest
 * </pre>
 *
 * @author sprinkle
 * @since 2026/3/21
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekProviderTest {

    private static LlmProvider provider;

    @BeforeAll
    static void setup() {
        LlmConfig config = LlmConfig.builder()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-chat")
                .build();
        provider = new OpenAiCompatibleProvider(config);
    }

    @Test
    void providerId_shouldBeOpenai() {
        assertThat(provider.providerId()).isEqualTo("openai");
    }

    @Test
    void simpleChat_shouldReturnValidResponse() {
        ChatRequest request = ChatRequest.builder()
                .systemPrompt("You are a helpful assistant. Respond briefly.")
                .messages(List.of(Message.UserMessage.of("请用一句话介绍一下你自己")))
                .maxTokens(100)
                .build();

        ChatResponse response = provider.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotEmpty();
        assertThat(response.stopReason()).isIn(StopReason.END_TURN, StopReason.MAX_TOKENS);
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().inputTokens()).isGreaterThan(0);
        assertThat(response.usage().outputTokens()).isGreaterThan(0);
        assertThat(response.modelId()).isNotBlank();

        String text = response.textContent();
        assertThat(text).isNotBlank();
        System.out.println("[DeepSeek 回复] " + text);
    }

    @Test
    void chatWithTool_shouldReturnToolCall() {
        ToolDefinition weatherTool = ToolDefinition.of("get_weather", "获取指定城市的天气",
                Map.of("type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "城市名称")
                        ),
                        "required", List.of("city")));

        ChatRequest request = ChatRequest.builder()
                .systemPrompt("You are a weather assistant. Always use the get_weather tool to answer weather questions.")
                .messages(List.of(Message.UserMessage.of("北京今天天气怎么样？")))
                .tools(List.of(weatherTool))
                .maxTokens(200)
                .build();

        ChatResponse response = provider.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotEmpty();

        if (response.stopReason() == StopReason.TOOL_USE) {
            assertThat(response.toolCalls()).isNotEmpty();
            ContentBlock.ToolUseBlock toolCall = response.toolCalls().getFirst();
            assertThat(toolCall.name()).isEqualTo("get_weather");
            assertThat(toolCall.id()).isNotBlank();
            assertThat(toolCall.input()).containsKey("city");
            System.out.println("[DeepSeek 工具调用] " + toolCall.name() + " -> " + toolCall.input());
        } else {
            System.out.println("[DeepSeek 直接回复] " + response.textContent());
        }
    }

    @Test
    void multiTurnConversation_shouldMaintainContext() {
        ChatRequest request1 = ChatRequest.builder()
                .systemPrompt("You are a helpful assistant. Remember the conversation context.")
                .messages(List.of(Message.UserMessage.of("我最喜欢的数字是42")))
                .maxTokens(100)
                .build();

        ChatResponse response1 = provider.chat(request1);
        assertThat(response1).isNotNull();
        System.out.println("[DeepSeek 第一轮] " + response1.textContent());

        ChatRequest request2 = ChatRequest.builder()
                .systemPrompt("You are a helpful assistant. Remember the conversation context.")
                .messages(List.of(
                        Message.UserMessage.of("我最喜欢的数字是42"),
                        new Message.AssistantMessage(response1.content(), response1.stopReason()),
                        Message.UserMessage.of("我刚才说我最喜欢的数字是什么？")
                ))
                .maxTokens(100)
                .build();

        ChatResponse response2 = provider.chat(request2);
        assertThat(response2).isNotNull();
        String text = response2.textContent();
        assertThat(text).isNotBlank();
        System.out.println("[DeepSeek 第二轮] " + text);
        assertThat(text).contains("42");
    }

    @Test
    void tokenUsage_shouldBeTracked() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.UserMessage.of("Hi")))
                .maxTokens(50)
                .build();

        ChatResponse response = provider.chat(request);

        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().inputTokens()).isGreaterThan(0);
        assertThat(response.usage().outputTokens()).isGreaterThan(0);
        assertThat(response.usage().totalTokens()).isGreaterThan(0);
        System.out.printf("[Token 用量] 输入: %d, 输出: %d, 总计: %d%n",
                response.usage().inputTokens(),
                response.usage().outputTokens(),
                response.usage().totalTokens());
    }
}
