package com.sprinkleclaw.workflow.orchestration;

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
