package icu.sprinkle.loom.workflow.orchestration.loop;

import java.util.function.Predicate;

/**
 * Loop 终止条件：当 {@link #shouldTerminate(Object)} 返回 true 时结束循环。
 *
 * @param <O> Loop body 的输出类型
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@FunctionalInterface
public interface LoopCondition<O> extends Predicate<O> {

    /**
     * 判断是否应终止循环。
     */
    boolean shouldTerminate(O output);

    @Override
    default boolean test(O o) {
        return shouldTerminate(o);
    }
}
