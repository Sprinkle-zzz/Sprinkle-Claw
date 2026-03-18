package com.sprinkleclaw.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将方法标记为 Agent 工具。
 * <p>被标注的方法会通过 {@link AnnotatedToolAdapter} 自动包装为 {@link com.sprinkleclaw.tool.AgentTool} 实例。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    /**
     * 工具名称。为空时默认使用方法名。
     */
    String name() default "";

    /**
     * 工具描述，展示给 LLM 帮助其判断何时使用此工具。
     */
    String description();

    /**
     * 工具超时时间（秒），默认 120 秒。
     */
    int timeoutSeconds() default 120;
}
