package com.sprinkleclaw.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述 {@link Tool} 标注方法的参数信息。
 * <p>用于生成 JSON Schema 时提供参数名称、描述和必填标识。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    /**
     * 参数名称。为空时使用反射获取的参数名（需 {@code -parameters} 编译标志）。
     */
    String name() default "";

    /**
     * 参数描述，展示给 LLM。
     */
    String description() default "";

    /**
     * 是否为必填参数，默认为 true。
     */
    boolean required() default true;
}
