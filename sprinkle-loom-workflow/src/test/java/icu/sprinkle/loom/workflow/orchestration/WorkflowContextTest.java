package icu.sprinkle.loom.workflow.orchestration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowContextTest {

    @Test
    void create_generatesUniqueId() {
        var ctx1 = WorkflowContext.create();
        var ctx2 = WorkflowContext.create();
        assertThat(ctx1.workflowId()).isNotEqualTo(ctx2.workflowId());
    }

    @Test
    void setAttribute_getAttribute_roundTrips() {
        var ctx = WorkflowContext.create();
        ctx.setAttribute("key", "value");
        assertThat(ctx.getAttribute("key", String.class)).isEqualTo("value");
    }

    @Test
    void attributesSnapshot_returnsImmutableAttributeCopy() {
        var ctx = WorkflowContext.create();
        ctx.setAttribute("key", "value");

        var snapshot = ctx.attributesSnapshot();
        ctx.setAttribute("key", "changed");

        assertThat(snapshot).containsEntry("key", "value");
        assertThatThrownBy(() -> snapshot.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateAttribute_withOverwriteReducer_replacesValue() {
        var ctx = WorkflowContext.create();
        ctx.setAttribute("status", "draft");

        String updated = ctx.updateAttribute("status", "done", StateReducer.overwrite());

        assertThat(updated).isEqualTo("done");
        assertThat(ctx.getAttribute("status", String.class)).isEqualTo("done");
    }

    @Test
    void updateAttribute_withAppendListReducer_appendsValues() {
        var ctx = WorkflowContext.create();
        ctx.updateAttribute("events", java.util.List.of("created"), StateReducer.<String>appendList());

        ctx.updateAttribute("events", java.util.List.of("validated", "finished"), StateReducer.<String>appendList());

        Object events = ctx.getAttribute("events", Object.class);
        assertThat(events).isInstanceOf(java.util.List.class);
        assertThat(events).isEqualTo(java.util.List.of("created", "validated", "finished"));
    }

    @Test
    void applyStateUpdate_mergesMultipleFields() {
        var ctx = WorkflowContext.create();
        ctx.setAttribute("metadata", java.util.Map.of("a", 1));
        var update = WorkflowStateUpdate.builder()
                .put("status", "ready")
                .put("metadata", java.util.Map.of("b", 2), StateReducer.<String, Integer>mergeMap())
                .build();

        ctx.applyStateUpdate(update);

        assertThat(ctx.getAttribute("status", String.class)).isEqualTo("ready");
        Object metadata = ctx.getAttribute("metadata", Object.class);
        assertThat(metadata)
                .isInstanceOf(java.util.Map.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Integer.class))
                .containsEntry("a", 1)
                .containsEntry("b", 2);
    }

    @Test
    void cancel_marksCancelled() {
        var ctx = WorkflowContext.create();
        assertThat(ctx.isCancelled()).isFalse();
        ctx.cancel();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void cancel_propagatesToChild() {
        var parent = WorkflowContext.create();
        var child = WorkflowContext.createChild(parent, "child");
        assertThat(child.isCancelled()).isFalse();
        parent.cancel();
        assertThat(child.isCancelled()).isTrue();
    }

    @Test
    void throwIfCancelled_whenCancelled_throwsWorkflowException() {
        var ctx = WorkflowContext.create();
        ctx.cancel();
        assertThatThrownBy(ctx::throwIfCancelled)
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void throwIfCancelled_whenNotCancelled_doesNotThrow() {
        var ctx = WorkflowContext.create();
        ctx.throwIfCancelled(); // should not throw
    }

    @Test
    void recordStep_storesResults() {
        var ctx = WorkflowContext.create();
        ctx.recordStep(StepResult.success("step1", "out1",
                java.time.Duration.ofMillis(10), java.time.Instant.now()));
        ctx.recordStep(StepResult.success("step2", "out2",
                java.time.Duration.ofMillis(20), java.time.Instant.now()));
        assertThat(ctx.stepResults()).hasSize(2);
        assertThat(ctx.stepResults().get(0).stepName()).isEqualTo("step1");
    }

    @Test
    void createChild_withNullParent_createsRoot() {
        var ctx = WorkflowContext.createChild(null, "test");
        assertThat(ctx.workflowId()).isNotEmpty();
        assertThat(ctx.isCancelled()).isFalse();
    }

    @Test
    void createChild_idContainsStepName() {
        var parent = WorkflowContext.create();
        var child = WorkflowContext.createChild(parent, "myStep");
        assertThat(child.workflowId()).contains("myStep");
    }
}
