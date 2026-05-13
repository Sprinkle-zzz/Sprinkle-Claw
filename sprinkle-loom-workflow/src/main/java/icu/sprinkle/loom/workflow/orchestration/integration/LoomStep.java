package icu.sprinkle.loom.workflow.orchestration.integration;

import icu.sprinkle.loom.workflow.orchestration.WorkflowContext;
import icu.sprinkle.loom.workflow.orchestration.WorkflowException;
import icu.sprinkle.loom.workflow.orchestration.WorkflowStep;

import java.util.function.Function;

/**
 * 将命令式 Loom 调用包装为 {@link WorkflowStep}。
 * <p>通过函数式接口解耦，避免 workflow 模块直接依赖 Loom 实例类型。</p>
 *
 * <pre>{@code
 * var loom = LoomBuilder.create().llmProvider(provider).build();
 * var step = new LoomStep(prompt -> loom.run(prompt).finalText());
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class LoomStep implements WorkflowStep<String, String> {

    private final Function<String, String> loomFunction;
    private final String stepName;

    public LoomStep(Function<String, String> loomFunction) {
        this("loom", loomFunction);
    }

    public LoomStep(String name, Function<String, String> loomFunction) {
        this.stepName = name;
        this.loomFunction = loomFunction;
    }

    @Override
    public String execute(String prompt, WorkflowContext ctx) throws WorkflowException {
        try {
            return loomFunction.apply(prompt);
        } catch (Exception e) {
            throw new WorkflowException(stepName, -1, e);
        }
    }

    @Override
    public String name() { return stepName; }
}
