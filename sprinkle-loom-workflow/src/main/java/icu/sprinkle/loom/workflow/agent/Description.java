package icu.sprinkle.loom.workflow.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数语义描述注解（参考 Koog {@code @LLMDescription}）。
 * <p>当方法使用参数模板时，{@code @Description} 的值会注入到 prompt 帮助 LLM 理解参数含义。
 * 在 {@code GENERATE_RESPONSE_TOOL} 模式下，也会进入 JSON Schema 的 {@code description} 字段。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Description {

    /** 参数描述文本 */
    String value();
}
