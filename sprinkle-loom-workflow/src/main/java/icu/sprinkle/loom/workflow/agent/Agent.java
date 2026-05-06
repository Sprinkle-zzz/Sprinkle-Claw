package icu.sprinkle.loom.workflow.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个接口为声明式 Agent。
 * <p>被标注的接口可通过 {@link AgentFactory#create(Class, icu.sprinkle.loom.llm.LlmProvider)}
 * 创建动态代理实例，方法调用将自动转换为 LLM 请求。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Agent {

    /**
     * 系统提示词，应用于接口所有方法（除非方法级 @SystemMessage 覆盖）
     */
    String[] systemPrompt() default {};

    /**
     * 多段系统提示词的拼接分隔符。
     */
    String systemPromptDelimiter() default "\n";

    /**
     * 接口级 system prompt 所在的 classpath 资源路径。
     */
    String systemPromptResource() default "";

    /**
     * 默认模型（可被 AgentFactory.create 参数覆盖）
     */
    String model() default "";

    /**
     * 默认最大迭代次数（有工具的方法走完整 Loop 时使用）
     */
    int maxIterations() default 5;

    /**
     * 结构化输出策略
     */
    StructuredOutputStrategy outputStrategy() default StructuredOutputStrategy.AUTO;

    /**
     * 自纠正最大重试次数
     */
    int maxCorrectionRetries() default 2;
}
