package com.sprinkleclaw.workflow.orchestration;

/**
 * Workflow 接口：可独立执行的编排单元。
 * <p>继承 {@link WorkflowStep}，使 Workflow 天然可嵌套——
 * 外层 Workflow 调用内层 {@code execute()} 时，内层使用外层传入的 context。</p>
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public interface Workflow<I, O> extends WorkflowStep<I, O> {

    /**
     * 执行 Workflow 并返回完整结果（创建新的根 context）。
     */
    WorkflowResult<O> run(I input);

    /**
     * 执行 Workflow 并传入已有 context（嵌套场景使用）。
     */
    WorkflowResult<O> run(I input, WorkflowContext parentContext);

    /**
     * 默认 execute 实现：调用 run() 后提取 output。
     */
    @Override
    default O execute(I input, WorkflowContext context) throws WorkflowException {
        WorkflowResult<O> result = run(input, context);
        if (!result.success() && result.error() != null) {
            throw result.error();
        }
        return result.output();
    }
}
