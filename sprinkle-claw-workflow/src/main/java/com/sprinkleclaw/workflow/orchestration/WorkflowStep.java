package com.sprinkleclaw.workflow.orchestration;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Workflow 步骤 SPI：接受输入、产出输出。
 * <p>作为函数式接口，可用 lambda 快速创建。</p>
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@FunctionalInterface
public interface WorkflowStep<I, O> {

    /**
     * 执行单步。
     *
     * @param input   输入
     * @param context 工作流上下文
     * @return 输出
     * @throws WorkflowException 执行失败时抛出
     */
    O execute(I input, WorkflowContext context) throws WorkflowException;

    /**
     * 步骤名称（用于 trace / error 信息）。
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 从普通函数创建步骤。
     */
    static <I, O> WorkflowStep<I, O> of(String name, Function<I, O> fn) {
        return new WorkflowStep<>() {
            @Override
            public O execute(I input, WorkflowContext ctx) {
                return fn.apply(input);
            }

            @Override
            public String name() { return name; }
        };
    }

    /**
     * 从需要 context 的函数创建步骤。
     */
    static <I, O> WorkflowStep<I, O> of(String name, BiFunction<I, WorkflowContext, O> fn) {
        return new WorkflowStep<>() {
            @Override
            public O execute(I input, WorkflowContext ctx) {
                return fn.apply(input, ctx);
            }

            @Override
            public String name() { return name; }
        };
    }
}
