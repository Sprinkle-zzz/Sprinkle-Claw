package icu.sprinkle.loom.workflow.orchestration.parallel;

import icu.sprinkle.loom.workflow.orchestration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 并行编排：同一输入分发到多个分支并行执行，结果由 {@link MergeStrategy} 合并。
 * <p>使用虚拟线程并行执行各分支。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class ParallelWorkflow<I, O> implements Workflow<I, O> {

    private final List<WorkflowStep<I, ?>> branches;
    private final MergeStrategy<O> merger;
    private final ErrorPolicy errorPolicy;

    public ParallelWorkflow(List<WorkflowStep<I, ?>> branches,
                            MergeStrategy<O> merger,
                            ErrorPolicy errorPolicy) {
        this.branches = List.copyOf(branches);
        this.merger = merger;
        this.errorPolicy = errorPolicy;
    }

    @Override
    public WorkflowResult<O> run(I input) {
        return run(input, null);
    }

    @Override
    public WorkflowResult<O> run(I input, WorkflowContext parentContext) {
        var ctx = WorkflowContext.createChild(parentContext, "parallel");
        var start = Instant.now();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = new ArrayList<>();
            for (var branch : branches) {
                futures.add(executor.submit(() -> {
                    ctx.throwIfCancelled();
                    var stepStart = Instant.now();
                    try {
                        Object result = branch.execute(input, ctx);
                        ctx.recordStep(StepResult.success(
                                branch.name(), result,
                                Duration.between(stepStart, Instant.now()), stepStart));
                        return result;
                    } catch (Exception e) {
                        ctx.recordStep(StepResult.failure(
                                branch.name(), e,
                                Duration.between(stepStart, Instant.now()), stepStart));
                        throw e;
                    }
                }));
            }

            List<Object> results = new ArrayList<>();
            WorkflowException firstError = null;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (errorPolicy == ErrorPolicy.FAIL_FAST) {
                        var ex = new WorkflowException(branches.get(i).name(), i, cause);
                        return WorkflowResult.failure(ex, ctx.stepResults(),
                                Duration.between(start, Instant.now()));
                    }
                    if (firstError == null) {
                        firstError = new WorkflowException(branches.get(i).name(), i, cause);
                    }
                    results.add(null);
                }
            }

            O merged = merger.merge(results);
            return WorkflowResult.success(merged, ctx.stepResults(),
                    Duration.between(start, Instant.now()));
        }
    }

    @Override
    public String name() { return "parallel"; }
}
