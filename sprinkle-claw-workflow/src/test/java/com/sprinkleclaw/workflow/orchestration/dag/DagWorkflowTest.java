package com.sprinkleclaw.workflow.orchestration.dag;

import com.sprinkleclaw.workflow.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DagWorkflowTest {

    @SuppressWarnings("unchecked")
    private static <I, O> DagNode node(String id, WorkflowStep<I, O> step) {
        return new DagNode(id, (WorkflowStep<Object, Object>) step);
    }

    @Test
    void run_linearDag_executesInOrder() {
        // A -> B -> C
        var nodes = new LinkedHashMap<String, DagNode>();
        nodes.put("A", node("A", WorkflowStep.of("A", (String s) -> s + "->A")));
        nodes.put("B", node("B", WorkflowStep.of("B", (String s) -> s + "->B")));
        nodes.put("C", node("C", WorkflowStep.of("C", (String s) -> s + "->C")));

        var edges = List.of(new DagEdge("A", "B"), new DagEdge("B", "C"));
        var wf = new DagWorkflow<String, String>(nodes, edges, "C");

        var result = wf.run("start");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("start->A->B->C");
    }

    @Test
    @SuppressWarnings("unchecked")
    void run_diamondDag_mergesInputs() {
        // A -> B, A -> C, B+C -> D
        var nodes = new LinkedHashMap<String, DagNode>();
        nodes.put("A", node("A", WorkflowStep.of("A", (String s) -> s + "-A")));
        nodes.put("B", node("B", WorkflowStep.of("B", (String s) -> s + "-B")));
        nodes.put("C", node("C", WorkflowStep.of("C", (String s) -> s + "-C")));
        nodes.put("D", node("D", WorkflowStep.of("D", (Object obj) -> {
            Map<String, Object> inputs = (Map<String, Object>) obj;
            return inputs.get("B") + "+" + inputs.get("C");
        })));

        var edges = List.of(
                new DagEdge("A", "B"), new DagEdge("A", "C"),
                new DagEdge("B", "D"), new DagEdge("C", "D")
        );
        var wf = new DagWorkflow<String, String>(nodes, edges, "D");

        var result = wf.run("x");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("B").contains("C");
    }

    @Test
    void run_cycleDetected_throwsOnConstruction() {
        var nodes = new LinkedHashMap<String, DagNode>();
        nodes.put("A", node("A", WorkflowStep.of("A", (String s) -> s)));
        nodes.put("B", node("B", WorkflowStep.of("B", (String s) -> s)));

        var edges = List.of(new DagEdge("A", "B"), new DagEdge("B", "A"));

        assertThatThrownBy(() -> new DagWorkflow<String, String>(nodes, edges, "B"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cycle");
    }

    @Test
    void run_sourceNodes_receiveInitialInput() {
        var nodes = new LinkedHashMap<String, DagNode>();
        nodes.put("A", node("A", WorkflowStep.of("A", (String s) -> "got:" + s)));

        var wf = new DagWorkflow<String, String>(nodes, List.of(), "A");

        var result = wf.run("hello");
        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("got:hello");
    }

    @Test
    void run_nodeThrows_returnsFailure() {
        var nodes = new LinkedHashMap<String, DagNode>();
        nodes.put("A", node("A", WorkflowStep.of("A", (String s) -> {
            throw new RuntimeException("boom");
        })));

        var wf = new DagWorkflow<String, String>(nodes, List.of(), "A");

        var result = wf.run("input");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
    }

    @Test
    void run_recordsStepResults() {
        var nodes = new LinkedHashMap<String, DagNode>();
        nodes.put("A", node("A", WorkflowStep.of("A", (String s) -> s + "A")));
        nodes.put("B", node("B", WorkflowStep.of("B", (String s) -> s + "B")));

        var edges = List.of(new DagEdge("A", "B"));
        var wf = new DagWorkflow<String, String>(nodes, edges, "B");

        var result = wf.run("x");
        assertThat(result.stepResults()).hasSize(2);
    }
}
