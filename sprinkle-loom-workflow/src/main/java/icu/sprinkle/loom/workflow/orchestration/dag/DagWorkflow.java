package icu.sprinkle.loom.workflow.orchestration.dag;

import icu.sprinkle.loom.workflow.orchestration.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * DAG 编排：有向无环图拓扑排序 + 同层并行执行。
 * <p>构建时进行环检测，运行时按拓扑层分组并行执行。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class DagWorkflow<I, O> implements Workflow<I, O> {

    private static final String INPUT_NODE = "__input__";

    private final Map<String, DagNode> nodes;
    private final List<DagEdge> edges;
    private final String outputNodeId;
    private final List<String> sourceNodeIds;
    private final List<List<String>> layers;

    public DagWorkflow(Map<String, DagNode> nodes, List<DagEdge> edges, String outputNodeId) {
        CycleDetector.detectOrThrow(nodes, edges);
        this.nodes = Map.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.outputNodeId = outputNodeId;
        this.layers = TopologicalSorter.sortIntoLayers(nodes, edges);

        // 找出没有入边的源节点（接收初始输入）
        Set<String> hasIncoming = new HashSet<>();
        for (var edge : edges) {
            hasIncoming.add(edge.to());
        }
        this.sourceNodeIds = nodes.keySet().stream()
                .filter(id -> !hasIncoming.contains(id))
                .toList();
    }

    @Override
    public WorkflowResult<O> run(I input) {
        return run(input, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public WorkflowResult<O> run(I input, WorkflowContext parentContext) {
        var ctx = WorkflowContext.createChild(parentContext, "dag");
        var start = Instant.now();
        var nodeOutputs = new ConcurrentHashMap<String, Object>();
        nodeOutputs.put(INPUT_NODE, input);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var layer : layers) {
                ctx.throwIfCancelled();
                List<Future<?>> futures = new ArrayList<>();

                for (var nodeId : layer) {
                    var node = nodes.get(nodeId);
                    futures.add(executor.submit(() -> {
                        ctx.throwIfCancelled();
                        var stepStart = Instant.now();
                        try {
                            Object nodeInput = resolveInput(nodeId, nodeOutputs);
                            Object result = node.step().execute(nodeInput, ctx);
                            nodeOutputs.put(nodeId, result);
                            ctx.recordStep(StepResult.success(
                                    nodeId, result,
                                    Duration.between(stepStart, Instant.now()), stepStart));
                        } catch (Exception e) {
                            ctx.recordStep(StepResult.failure(
                                    nodeId, e,
                                    Duration.between(stepStart, Instant.now()), stepStart));
                            throw e;
                        }
                        return null;
                    }));
                }

                // 等待当前层完成
                for (var future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof WorkflowException we) {
                            return WorkflowResult.failure(we, ctx.stepResults(),
                                    Duration.between(start, Instant.now()));
                        }
                        var ex = new WorkflowException("dag", -1, cause);
                        return WorkflowResult.failure(ex, ctx.stepResults(),
                                Duration.between(start, Instant.now()));
                    }
                }
            }
        }

        O finalOutput = (O) nodeOutputs.get(outputNodeId);
        return WorkflowResult.success(finalOutput, ctx.stepResults(),
                Duration.between(start, Instant.now()));
    }

    /**
     * 解析节点输入：如果只有一个上游，直接传其输出；多个上游则以 Map 传入。
     * 源节点（无上游）接收初始输入。
     */
    private Object resolveInput(String nodeId, Map<String, Object> nodeOutputs) {
        List<String> upstreams = edges.stream()
                .filter(e -> e.to().equals(nodeId))
                .map(DagEdge::from)
                .toList();

        if (upstreams.isEmpty()) {
            return nodeOutputs.get(INPUT_NODE);
        }
        if (upstreams.size() == 1) {
            return nodeOutputs.get(upstreams.getFirst());
        }
        // 多上游：以 Map<nodeId, output> 传入
        Map<String, Object> multiInput = new LinkedHashMap<>();
        for (var upstream : upstreams) {
            multiInput.put(upstream, nodeOutputs.get(upstream));
        }
        return multiInput;
    }

    @Override
    public String name() { return "dag"; }
}
