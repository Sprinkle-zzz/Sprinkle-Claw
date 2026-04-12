package com.sprinkleclaw.workflow.orchestration.integration;

import com.sprinkleclaw.workflow.orchestration.WorkflowContext;
import com.sprinkleclaw.workflow.orchestration.WorkflowException;
import com.sprinkleclaw.workflow.orchestration.WorkflowStep;

import java.util.function.Function;

/**
 * 将命令式 Claw 调用包装为 {@link WorkflowStep}。
 * <p>通过函数式接口解耦，避免 workflow 模块直接依赖 Claw 实例类型。</p>
 *
 * <pre>{@code
 * var claw = ClawBuilder.create().llmProvider(provider).build();
 * var step = new ClawStep(prompt -> claw.run(prompt).finalText());
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class ClawStep implements WorkflowStep<String, String> {

    private final Function<String, String> clawFunction;
    private final String stepName;

    public ClawStep(Function<String, String> clawFunction) {
        this("claw", clawFunction);
    }

    public ClawStep(String name, Function<String, String> clawFunction) {
        this.stepName = name;
        this.clawFunction = clawFunction;
    }

    @Override
    public String execute(String prompt, WorkflowContext ctx) throws WorkflowException {
        try {
            return clawFunction.apply(prompt);
        } catch (Exception e) {
            throw new WorkflowException(stepName, -1, e);
        }
    }

    @Override
    public String name() { return stepName; }
}
