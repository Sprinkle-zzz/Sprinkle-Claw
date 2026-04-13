package com.sprinkleclaw.workflow.orchestration.router;

import com.sprinkleclaw.workflow.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouterWorkflowTest {

    @Test
    void run_matchingRoute_executesCorrectBranch() {
        var wf = new RouterWorkflow<>(
                (String s) -> s.length() > 5 ? "long" : "short",
                Map.of(
                        "long", WorkflowStep.of("long", (String s) -> "LONG:" + s),
                        "short", WorkflowStep.of("short", (String s) -> "SHORT:" + s)
                ),
                null
        );
        var result = wf.run("hello world");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("LONG:hello world");
    }

    @Test
    void run_noMatchingRoute_usesDefault() {
        var wf = new RouterWorkflow<>(
                (String s) -> "unknown",
                Map.of("a", WorkflowStep.of("a", (String s) -> "A")),
                WorkflowStep.of("default", (String s) -> "DEFAULT:" + s)
        );
        var result = wf.run("test");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("DEFAULT:test");
    }

    @Test
    void run_noMatchAndNoDefault_returnsFailure() {
        var wf = new RouterWorkflow<>(
                (String s) -> "unknown",
                Map.of("a", WorkflowStep.of("a", (String s) -> "A")),
                null
        );
        var result = wf.run("test");
        assertThat(result.success()).isFalse();
        assertThat(result.error().getMessage()).contains("No route found");
    }

    @Test
    void run_routeThrows_returnsFailure() {
        var wf = new RouterWorkflow<>(
                (String s) -> "boom",
                Map.of("boom", WorkflowStep.of("boom", (String s) -> {
                    throw new RuntimeException("fail");
                })),
                null
        );
        var result = wf.run("test");
        assertThat(result.success()).isFalse();
    }

    @Test
    void run_recordsStepResult() {
        var wf = new RouterWorkflow<>(
                (String s) -> "a",
                Map.of("a", WorkflowStep.of("route-a", (String s) -> "result")),
                null
        );
        var result = wf.run("input");
        assertThat(result.stepResults()).hasSize(1);
        assertThat(result.stepResults().getFirst().stepName()).isEqualTo("route-a");
    }
}
