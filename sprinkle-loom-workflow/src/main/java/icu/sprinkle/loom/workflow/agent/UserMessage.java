package icu.sprinkle.loom.workflow.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法参数为 user message 内容，或在方法级定义 user message 模板。
 * <p>支持三种模式：</p>
 * <ul>
 *   <li>模式 A：单参数，{@code String summarize(@UserMessage String text)}，参数直接作为 user message</li>
 *   <li>模式 B：方法级模板，{@code @UserMessage("Translate '{text}' to {lang}") String translate(String text, String lang)}</li>
 *   <li>模式 C：参数标注 + 方法级 prompt，多参数组合</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UserMessage {

    /**
     * 可选：prompt 模板，使用 {paramName} 占位
     */
    String value() default "";

    /**
     * 方法级 user prompt 模板所在的 classpath 资源路径。
     */
    String fromResource() default "";
}
