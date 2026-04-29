package icu.sprinkle.loom.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 API 是稳定的：在主版本号内不会发生破坏性变更。
 *
 * <p>稳定 API 的承诺：</p>
 * <ul>
 *   <li>方法签名不会改变（参数顺序、类型、返回类型）</li>
 *   <li>语义不会改变（行为、副作用、并发模型）</li>
 *   <li>除非主版本号升级，否则不会被删除</li>
 * </ul>
 *
 * <p>未标注的 API 默认按 {@link Experimental} 对待——0.x 阶段大部分 API 仍属实验性，
 * 仅明确标 {@code @Stable} 的 API 才适合生产代码长期依赖。</p>
 *
 * @author sprinkle
 * @since 0.10.0 (MVP9)
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
public @interface Stable {
}
