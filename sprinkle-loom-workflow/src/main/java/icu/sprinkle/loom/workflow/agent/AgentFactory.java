package icu.sprinkle.loom.workflow.agent;

import icu.sprinkle.loom.llm.LlmProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * 声明式 Agent 工厂：从 {@code @Agent} 注解的接口创建动态代理实例。
 * <p>代理实例的每次方法调用会自动构造 LLM 请求、调用 LLM 并返回结构化结果。</p>
 *
 * <pre>{@code
 * @Agent(systemPrompt = "You are a code review assistant.")
 * public interface CodeReviewer {
 *     String review(@UserMessage String code);
 * }
 *
 * CodeReviewer reviewer = AgentFactory.create(CodeReviewer.class, llmProvider);
 * String result = reviewer.review("public void login(String password) { ... }");
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class AgentFactory {

    private AgentFactory() {
    }

    /**
     * 基础入口：使用 {@link LlmProvider} 创建代理实例。
     *
     * @param agentInterface 带有 {@link Agent} 注解的接口类
     * @param provider       LLM 提供者
     * @param <T>            接口类型
     * @return 代理实例
     */
    public static <T> T create(Class<T> agentInterface, LlmProvider provider) {
        return create(agentInterface, AgentFactoryConfig.defaults().llmProvider(provider).build());
    }

    /**
     * 完整入口：使用 {@link AgentFactoryConfig} 创建代理实例。
     *
     * @param agentInterface 带有 {@link Agent} 注解的接口类
     * @param config         Agent 工厂配置
     * @param <T>            接口类型
     * @return 代理实例
     */
    public static <T> T create(Class<T> agentInterface, AgentFactoryConfig config) {
        validateInterface(agentInterface);
        Agent agentAnnotation = agentInterface.getAnnotation(Agent.class);
        Map<Method, AgentMethodMetadata> metadata = AgentMethodCache.get(agentInterface);
        String systemPrompt = PromptResources.resolve(agentInterface,
                agentAnnotation.systemPrompt(), agentAnnotation.systemPromptResource());

        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
                agentInterface.getClassLoader(),
                new Class<?>[]{agentInterface},
                new AgentInvocationHandler(metadata, config, systemPrompt)
        );
        return proxy;
    }

    private static void validateInterface(Class<?> cls) {
        if (!cls.isInterface()) {
            throw new IllegalArgumentException(cls.getName() + " must be an interface");
        }
        if (!cls.isAnnotationPresent(Agent.class)) {
            throw new IllegalArgumentException(cls.getName() + " is missing @Agent annotation");
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.isDefault()) {
                continue;
            }
            AgentMethodValidator.validate(m);
        }
    }
}
