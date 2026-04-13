package com.sprinkleclaw.workflow.orchestration.loop;

import com.sprinkleclaw.workflow.orchestration.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopWorkflowTest {

    @Test
    void run_terminatesOnCondition_returnsSuccess() {
        var wf = new LoopWorkflow<>(
                WorkflowStep.of("increment", (Integer n) -> n + 1),
                n -> n,                    // feedback: output → next input
                n -> n >= 5,               // terminate when >= 5
                10
        );
        var result = wf.run(0);
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo(5);
    }

    @Test
    void run_maxIterationsExceeded_returnsFailure() {
        var wf = new LoopWorkflow<>(
                WorkflowStep.of("identity", (Integer n) -> n + 1),
                n -> n,
                n -> n >= 100,             // will never meet in 3 iterations
                3
        );
        var result = wf.run(0);
        assertThat(result.success()).isFalse();
        assertThat(result.error().getMessage()).contains("Max iterations");
    }

    @Test
    void run_feedbackFunction_transformsOutputToInput() {
        var wf = new LoopWorkflow<>(
                WorkflowStep.of("double", (Integer n) -> n * 2),
                n -> n,                    // pass output directly
                n -> n >= 16,
                10
        );
        // 1 → 2 → 4 → 8 → 16 (done)
        var result = wf.run(1);
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo(16);
    }

    @Test
    void run_bodyThrows_returnsFailure() {
        WorkflowStep<Integer, Integer> boom = WorkflowStep.of("boom", (Integer n) -> {
            throw new RuntimeException("fail");
        });
        var wf = new LoopWorkflow<>(boom, n -> n, n -> false, 5);
        var result = wf.run(0);
        assertThat(result.success()).isFalse();
    }

    @Test
    void run_cancellation_stopsLoop() {
        var ctx = WorkflowContext.create();
        ctx.cancel();
        var wf = new LoopWorkflow<>(
                WorkflowStep.of("step", (Integer n) -> n + 1),
                n -> n,
                n -> n >= 10,
                20
        );
        assertThatThrownBy(() -> wf.run(0, ctx))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void run_recordsPerIterationStepResults() {
        var wf = new LoopWorkflow<>(
                WorkflowStep.of("inc", (Integer n) -> n + 1),
                n -> n,
                n -> n >= 3,
                10
        );
        var result = wf.run(0);
        assertThat(result.stepResults()).hasSize(3);
        assertThat(result.stepResults().get(0).stepName()).contains("[0]");
        assertThat(result.stepResults().get(1).stepName()).contains("[1]");
    }

    @Test
    void run_stagnationDetected_returnsFailure() {
        // body always returns same value, triggering stagnation guard
        var wf = new LoopWorkflow<>(
                WorkflowStep.of("constant", (Integer n) -> 42),
                n -> n,
                n -> false,  // never terminates
                10
        );
        assertThatThrownBy(() -> wf.run(0))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("stagnation");
    }
}
