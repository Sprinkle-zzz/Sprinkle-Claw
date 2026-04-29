package icu.sprinkle.loom.workflow.orchestration.parallel;

import icu.sprinkle.loom.workflow.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelWorkflowTest {

    @Test
    void run_twoBranches_mergesResults() {
        var wf = new ParallelWorkflow<>(
                List.of(
                        WorkflowStep.of("upper", (String s) -> (Object) s.toUpperCase()),
                        WorkflowStep.of("length", (String s) -> (Object) s.length())
                ),
                results -> results.get(0).toString() + ":" + results.get(1).toString(),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("hello");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("HELLO:5");
    }

    @Test
    void run_executesInParallel() {
        AtomicInteger counter = new AtomicInteger(0);
        var wf = new ParallelWorkflow<>(
                List.of(
                        WorkflowStep.of("a", (String s) -> { counter.incrementAndGet(); return (Object) "a"; }),
                        WorkflowStep.of("b", (String s) -> { counter.incrementAndGet(); return (Object) "b"; })
                ),
                results -> results.size(),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("input");
        assertThat(result.success()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void run_branchThrows_failFast_returnsFailure() {
        var wf = new ParallelWorkflow<>(
                List.of(
                        WorkflowStep.of("ok", (String s) -> (Object) "ok"),
                        WorkflowStep.of("boom", (String s) -> { throw new RuntimeException("fail"); })
                ),
                results -> "merged",
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("input");
        assertThat(result.success()).isFalse();
    }

    @Test
    void run_branchThrows_continue_mergesWithNull() {
        var wf = new ParallelWorkflow<>(
                List.of(
                        WorkflowStep.of("ok", (String s) -> (Object) "ok"),
                        WorkflowStep.of("boom", (String s) -> { throw new RuntimeException("fail"); })
                ),
                results -> results.stream().filter(r -> r != null).count(),
                ErrorPolicy.CONTINUE
        );
        var result = wf.run("input");
        assertThat(result.success()).isTrue();
        assertThat((long) result.output()).isEqualTo(1L);
    }

    @Test
    void run_recordsStepResults() {
        var wf = new ParallelWorkflow<>(
                List.of(
                        WorkflowStep.of("a", (String s) -> (Object) "a"),
                        WorkflowStep.of("b", (String s) -> (Object) "b")
                ),
                results -> "done",
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("input");
        assertThat(result.stepResults()).hasSize(2);
    }
}
