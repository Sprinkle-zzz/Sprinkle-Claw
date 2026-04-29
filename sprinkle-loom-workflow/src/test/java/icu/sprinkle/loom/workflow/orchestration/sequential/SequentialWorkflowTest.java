package icu.sprinkle.loom.workflow.orchestration.sequential;

import icu.sprinkle.loom.workflow.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequentialWorkflowTest {

    @Test
    void run_twoSteps_chainsOutput() {
        var wf = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("upper", (String s) -> s.toUpperCase()),
                        WorkflowStep.of("exclaim", (String s) -> s + "!")
                ),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("hello");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("HELLO!");
    }

    @Test
    void run_stepThrows_failFast_returnsFailure() {
        var wf = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("ok", (String s) -> s),
                        WorkflowStep.of("boom", (String s) -> { throw new RuntimeException("fail"); })
                ),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("input");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
        assertThat(result.error().stepName()).isEqualTo("boom");
    }

    @Test
    void run_stepThrows_continue_continuesExecution() {
        var wf = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("boom", (String s) -> { throw new RuntimeException("fail"); }),
                        WorkflowStep.of("after", (String s) -> s + "-after")
                ),
                ErrorPolicy.CONTINUE
        );
        var result = wf.run("input");
        // CONTINUE mode: keeps going despite error
        assertThat(result.success()).isTrue();
    }

    @Test
    void run_recordsStepResults() {
        var wf = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("step1", (String s) -> s + "1"),
                        WorkflowStep.of("step2", (String s) -> s + "2")
                ),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("x");
        assertThat(result.stepResults()).hasSize(2);
        assertThat(result.stepResults().get(0).stepName()).isEqualTo("step1");
        assertThat(result.stepResults().get(0).success()).isTrue();
        assertThat(result.stepResults().get(1).stepName()).isEqualTo("step2");
    }

    @Test
    void run_cancellation_throwsWorkflowException() {
        var ctx = WorkflowContext.create();
        ctx.cancel();
        var wf = new SequentialWorkflow<String, String>(
                List.of(WorkflowStep.of("step", (String s) -> s)),
                ErrorPolicy.FAIL_FAST
        );
        assertThatThrownBy(() -> wf.run("input", ctx))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void run_withContext_passesContextToSteps() {
        var wf = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("write", (String s, WorkflowContext ctx) -> {
                            ctx.setAttribute("key", "written");
                            return s;
                        }),
                        WorkflowStep.of("read", (String s, WorkflowContext ctx) -> {
                            String val = ctx.getAttribute("key", String.class);
                            return s + "-" + val;
                        })
                ),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run("data");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("data-written");
    }

    @Test
    void run_singleStep_returnsDirectly() {
        var wf = new SequentialWorkflow<Integer, Integer>(
                List.of(WorkflowStep.of("double", (Integer n) -> n * 2)),
                ErrorPolicy.FAIL_FAST
        );
        var result = wf.run(5);
        assertThat(result.output()).isEqualTo(10);
    }
}
