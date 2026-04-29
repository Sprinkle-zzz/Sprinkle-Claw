package icu.sprinkle.loom.workflow.orchestration.conditional;

import icu.sprinkle.loom.workflow.orchestration.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionalWorkflowTest {

    @Test
    void run_predicateTrue_executesThenBranch() {
        var wf = new ConditionalWorkflow<>(
                (Integer n) -> n > 0,
                WorkflowStep.of("positive", (Integer n) -> "positive"),
                WorkflowStep.of("negative", (Integer n) -> "negative")
        );
        var result = wf.run(5);
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("positive");
    }

    @Test
    void run_predicateFalse_executesOtherwiseBranch() {
        var wf = new ConditionalWorkflow<>(
                (Integer n) -> n > 0,
                WorkflowStep.of("positive", (Integer n) -> "positive"),
                WorkflowStep.of("negative", (Integer n) -> "negative")
        );
        var result = wf.run(-1);
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("negative");
    }

    @Test
    void run_branchThrows_returnsFailure() {
        var wf = new ConditionalWorkflow<>(
                (String s) -> true,
                WorkflowStep.of("boom", (String s) -> { throw new RuntimeException("fail"); }),
                WorkflowStep.of("ok", (String s) -> s)
        );
        var result = wf.run("test");
        assertThat(result.success()).isFalse();
    }

    @Test
    void run_recordsChosenBranchStepResult() {
        var wf = new ConditionalWorkflow<>(
                (String s) -> s.startsWith("a"),
                WorkflowStep.of("a-branch", (String s) -> s + "-A"),
                WorkflowStep.of("other-branch", (String s) -> s + "-other")
        );
        var result = wf.run("abc");
        assertThat(result.stepResults()).hasSize(1);
        assertThat(result.stepResults().getFirst().stepName()).isEqualTo("a-branch");
    }

    @Test
    void run_totalDuration_isNonNegative() {
        var wf = new ConditionalWorkflow<>(
                (String s) -> true,
                WorkflowStep.of("fast", (String s) -> s),
                WorkflowStep.of("unused", (String s) -> s)
        );
        var result = wf.run("x");
        assertThat(result.totalDuration()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
    }
}
