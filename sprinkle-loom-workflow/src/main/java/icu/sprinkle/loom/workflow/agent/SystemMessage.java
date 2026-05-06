package icu.sprinkle.loom.workflow.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级系统提示词，覆盖 {@link Agent#systemPrompt()}。
 * <p>也可标注在参数上，此时参数值将追加到 system prompt。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemMessage {

    /**
     * 方法级系统提示（覆盖 @Agent.systemPrompt）
     */
    String[] value() default {};

    /**
     * 多段方法级系统提示的拼接分隔符。
     */
    String delimiter() default "\n";

    /**
     * 方法级 system prompt 所在的 classpath 资源路径。
     */
    String fromResource() default "";
}
