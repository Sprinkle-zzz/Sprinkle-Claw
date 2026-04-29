package icu.sprinkle.loom.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 API 是实验性的：在小版本之间可能发生破坏性变更（签名 / 语义 / 删除）。
 *
 * <p>实验性 API 的特点：</p>
 * <ul>
 *   <li>正在收集使用反馈，可能根据实际使用场景调整设计</li>
 *   <li>可能在任意小版本被签名变更或重命名</li>
 *   <li>可能被删除（替换为更好的设计）</li>
 * </ul>
 *
 * <p>使用建议：可以在生产代码中使用，但应当在升级 SDK 版本时密切关注 CHANGELOG。
 * 0.x 阶段未明确标 {@link Stable} 的 API 默认按本注解对待。</p>
 *
 * @author sprinkle
 * @since 0.10.0 (MVP9)
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
public @interface Experimental {

    /**
     * 可选：说明实验状态的备注（已知风险、设计开放问题等）。
     */
    String value() default "";
}
