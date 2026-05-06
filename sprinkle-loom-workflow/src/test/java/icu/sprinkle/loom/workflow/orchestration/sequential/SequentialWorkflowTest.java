package icu.sprinkle.loom.workflow.orchestration.sequential;

import icu.sprinkle.loom.workflow.orchestration.*;
import icu.sprinkle.loom.workflow.orchestration.checkpoint.InMemoryWorkflowCheckpointStore;
import icu.sprinkle.loom.workflow.orchestration.checkpoint.WorkflowCheckpoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void run_withCheckpointStore_savesCheckpointAfterEachSuccessfulStep() {
        var store = new InMemoryWorkflowCheckpointStore();
        var parent = WorkflowContext.create();
        var wf = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("write", (String s, WorkflowContext ctx) -> {
                            ctx.setAttribute("stage", "written");
                            return s + "-1";
                        }),
                        WorkflowStep.of("finish", (String s) -> s + "-2")
                ),
                ErrorPolicy.FAIL_FAST,
                store
        );

        var result = wf.run("input", parent);
        var checkpoints = store.list(parent.workflowId() + "/sequential");

        assertThat(result.success()).isTrue();
        assertThat(checkpoints).hasSize(2);
        assertThat(checkpoints.get(0).stepName()).isEqualTo("write");
        assertThat(checkpoints.get(0).attributes()).containsEntry("stage", "written");
        assertThat(checkpoints.get(1).output()).isEqualTo("input-1-2");
        assertThat(store.loadLatest(parent.workflowId() + "/sequential"))
                .hasValueSatisfying(checkpoint -> assertThat(checkpoint.stepName()).isEqualTo("finish"));
    }

    @Test
    void builder_withCheckpointStore_passesStoreToSequentialWorkflow() {
        var store = new InMemoryWorkflowCheckpointStore();
        var parent = WorkflowContext.create();
        var workflow = WorkflowBuilder.<String>start()
                .checkpointStore(store)
                .then("upper", String::toUpperCase)
                .then("suffix", value -> value + "!")
                .build();

        var result = workflow.run("hello", parent);

        assertThat(result.output()).isEqualTo("HELLO!");
        assertThat(store.list(parent.workflowId() + "/sequential")).hasSize(2);
    }

    @Test
    void resumeFrom_continuesFromNextStep() {
        AtomicInteger firstStepCalls = new AtomicInteger();
        var workflow = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("first", (String s) -> {
                            firstStepCalls.incrementAndGet();
                            return s + "-first";
                        }),
                        WorkflowStep.of("second", (String s) -> s + "-second")
                ),
                ErrorPolicy.FAIL_FAST
        );
        var checkpoint = new WorkflowCheckpoint(
                "workflow-1", "first", 0, "input-first",
                Map.of(), List.of(StepResult.success("first", "input-first",
                java.time.Duration.ofMillis(1), Instant.now())), Instant.now());

        var result = workflow.resumeFrom(checkpoint);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("input-first-second");
        assertThat(firstStepCalls).hasValue(0);
        assertThat(result.stepResults()).hasSize(2);
    }

    @Test
    void resumeFrom_whenCheckpointIsLastStep_returnsCheckpointOutput() {
        var workflow = new SequentialWorkflow<String, String>(
                List.of(WorkflowStep.of("only", (String s) -> s + "-done")),
                ErrorPolicy.FAIL_FAST
        );
        var checkpoint = new WorkflowCheckpoint(
                "workflow-1", "only", 0, "input-done",
                Map.of(), List.of(StepResult.success("only", "input-done",
                java.time.Duration.ofMillis(1), Instant.now())), Instant.now());

        var result = workflow.resumeFrom(checkpoint);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("input-done");
        assertThat(result.stepResults()).hasSize(1);
    }

    @Test
    void resumeFrom_restoresContextAttributes() {
        var workflow = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("restore", (String s, WorkflowContext ctx) ->
                                s + "-" + ctx.getAttribute("stage", String.class)),
                        WorkflowStep.of("finish", (String s) -> s + "-done")
                ),
                ErrorPolicy.FAIL_FAST
        );
        var checkpoint = new WorkflowCheckpoint(
                "workflow-1", "restore", 0, "input-parsed",
                Map.of("stage", "parsed"),
                List.of(StepResult.success("restore", "input-parsed",
                        java.time.Duration.ofMillis(1), Instant.now())),
                Instant.now());

        var result = workflow.resumeFrom(checkpoint);

        assertThat(result.output()).isEqualTo("input-parsed-done");
    }

    @Test
    void resumeFrom_continuesSavingCheckpoints() {
        var store = new InMemoryWorkflowCheckpointStore();
        var workflow = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("first", (String s) -> s + "-first"),
                        WorkflowStep.of("second", (String s) -> s + "-second")
                ),
                ErrorPolicy.FAIL_FAST,
                store
        );
        var checkpoint = new WorkflowCheckpoint(
                "workflow-1", "first", 0, "input-first",
                Map.of(), List.of(StepResult.success("first", "input-first",
                java.time.Duration.ofMillis(1), Instant.now())), Instant.now());

        workflow.resumeFrom(checkpoint);

        assertThat(store.list("workflow-1")).hasSize(1);
        assertThat(store.loadLatest("workflow-1"))
                .hasValueSatisfying(saved -> {
                    assertThat(saved.stepName()).isEqualTo("second");
                    assertThat(saved.output()).isEqualTo("input-first-second");
                });
    }

    @Test
    void resumeFrom_stepNameMismatch_throwsIllegalArgument() {
        var workflow = new SequentialWorkflow<String, String>(
                List.of(WorkflowStep.of("expected", (String s) -> s)),
                ErrorPolicy.FAIL_FAST
        );
        var checkpoint = new WorkflowCheckpoint(
                "workflow-1", "actual", 0, "input",
                Map.of(), List.of(), Instant.now());

        assertThatThrownBy(() -> workflow.resumeFrom(checkpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Checkpoint step mismatch");
    }

    @Test
    void resumeFrom_stepIndexOutOfRange_throwsIllegalArgument() {
        var workflow = new SequentialWorkflow<String, String>(
                List.of(WorkflowStep.of("step", (String s) -> s)),
                ErrorPolicy.FAIL_FAST
        );
        var checkpoint = new WorkflowCheckpoint(
                "workflow-1", "step", 1, "input",
                Map.of(), List.of(), Instant.now());

        assertThatThrownBy(() -> workflow.resumeFrom(checkpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void resumeLatest_loadsLatestCheckpointFromStore() {
        var store = new InMemoryWorkflowCheckpointStore();
        var workflow = new SequentialWorkflow<String, String>(
                List.of(
                        WorkflowStep.of("first", (String s) -> s + "-first"),
                        WorkflowStep.of("second", (String s) -> s + "-second")
                ),
                ErrorPolicy.FAIL_FAST,
                store
        );
        store.save(new WorkflowCheckpoint(
                "workflow-1", "first", 0, "input-first",
                Map.of(), List.of(StepResult.success("first", "input-first",
                java.time.Duration.ofMillis(1), Instant.now())), Instant.now()));

        var result = workflow.resumeLatest("workflow-1");

        assertThat(result.output()).isEqualTo("input-first-second");
    }

    @Test
    void resumeLatest_withoutStore_throwsIllegalState() {
        var workflow = new SequentialWorkflow<String, String>(
                List.of(WorkflowStep.of("step", (String s) -> s)),
                ErrorPolicy.FAIL_FAST
        );

        assertThatThrownBy(() -> workflow.resumeLatest("workflow-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checkpointStore is required");
    }

    @Test
    void resumeLatest_withoutCheckpoint_throwsIllegalArgument() {
        var workflow = new SequentialWorkflow<String, String>(
                List.of(WorkflowStep.of("step", (String s) -> s)),
                ErrorPolicy.FAIL_FAST,
                new InMemoryWorkflowCheckpointStore()
        );

        assertThatThrownBy(() -> workflow.resumeLatest("workflow-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No checkpoint found");
    }

    @Test
    void builder_buildSequential_returnsSequentialWorkflowForResumeApi() {
        var store = new InMemoryWorkflowCheckpointStore();
        SequentialWorkflow<String, String> workflow = WorkflowBuilder.<String>start()
                .checkpointStore(store)
                .then("first", value -> value + "-first")
                .then("second", value -> value + "-second")
                .buildSequential();

        store.save(new WorkflowCheckpoint(
                "workflow-1", "first", 0, "input-first",
                Map.of(), List.of(StepResult.success("first", "input-first",
                java.time.Duration.ofMillis(1), Instant.now())), Instant.now()));

        assertThat(workflow.resumeLatest("workflow-1").output()).isEqualTo("input-first-second");
    }
}
