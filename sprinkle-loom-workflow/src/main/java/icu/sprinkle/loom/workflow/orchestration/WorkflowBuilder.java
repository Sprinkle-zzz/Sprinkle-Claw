package icu.sprinkle.loom.workflow.orchestration;

import icu.sprinkle.loom.workflow.orchestration.conditional.ConditionalWorkflow;
import icu.sprinkle.loom.workflow.orchestration.dag.DagEdge;
import icu.sprinkle.loom.workflow.orchestration.dag.DagNode;
import icu.sprinkle.loom.workflow.orchestration.dag.DagWorkflow;
import icu.sprinkle.loom.workflow.orchestration.checkpoint.WorkflowCheckpointStore;
import icu.sprinkle.loom.workflow.orchestration.loop.LoopWorkflow;
import icu.sprinkle.loom.workflow.orchestration.parallel.MergeStrategy;
import icu.sprinkle.loom.workflow.orchestration.parallel.ParallelWorkflow;
import icu.sprinkle.loom.workflow.orchestration.router.RouterWorkflow;
import icu.sprinkle.loom.workflow.orchestration.sequential.SequentialWorkflow;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 类型安全的 Workflow 链式构建器。
 * <p>提供 Sequential / Parallel / Loop / Conditional / Router / DAG 六种编排模式的构建入口。</p>
 *
 * <pre>{@code
 * Workflow<String, Result> wf = WorkflowBuilder
 *     .<String>start()
 *     .then("parse", new ParseStep())
 *     .then("analyze", new AnalyzeStep())
 *     .build();
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class WorkflowBuilder {

    private WorkflowBuilder() {}

    // ── Sequential ──────────────────────────────────────────────────────

    /**
     * 开始构建 Sequential Workflow。
     */
    public static <I> SequentialBuilder<I, I> start() {
        return new SequentialBuilder<>(new ArrayList<>(), null);
    }

    public static final class SequentialBuilder<I, C> {
        private final List<WorkflowStep<?, ?>> steps;
        private final WorkflowCheckpointStore checkpointStore;

        SequentialBuilder(List<WorkflowStep<?, ?>> steps, WorkflowCheckpointStore checkpointStore) {
            this.steps = steps;
            this.checkpointStore = checkpointStore;
        }

        public <N> SequentialBuilder<I, N> then(WorkflowStep<C, N> next) {
            var copy = new ArrayList<>(steps);
            copy.add(next);
            return new SequentialBuilder<>(copy, checkpointStore);
        }

        public <N> SequentialBuilder<I, N> then(String name, Function<C, N> fn) {
            return then(WorkflowStep.of(name, fn));
        }

        /**
         * 为 Sequential Workflow 配置 checkpoint 存储。
         *
         * @param checkpointStore checkpoint 存储；为 {@code null} 时关闭 checkpoint
         * @return 新的构建器
         */
        public SequentialBuilder<I, C> checkpointStore(WorkflowCheckpointStore checkpointStore) {
            return new SequentialBuilder<>(steps, checkpointStore);
        }

        public Workflow<I, C> build() {
            return new SequentialWorkflow<>(steps, ErrorPolicy.FAIL_FAST, checkpointStore);
        }

        public Workflow<I, C> build(ErrorPolicy policy) {
            return new SequentialWorkflow<>(steps, policy, checkpointStore);
        }

        /**
         * 构建具体的 {@link SequentialWorkflow} 实例。
         * <p>
         * 当调用方需要使用 `resumeFrom` 或 `resumeLatest` 等顺序 Workflow 专有能力时，
         * 应使用该方法；只需要通用执行能力时继续使用 {@link #build()}。
         *
         * @return 顺序 Workflow 实例
         */
        public SequentialWorkflow<I, C> buildSequential() {
            return buildSequential(ErrorPolicy.FAIL_FAST);
        }

        /**
         * 构建具体的 {@link SequentialWorkflow} 实例。
         *
         * @param policy 错误处理策略
         * @return 顺序 Workflow 实例
         */
        public SequentialWorkflow<I, C> buildSequential(ErrorPolicy policy) {
            return new SequentialWorkflow<>(steps, policy, checkpointStore);
        }
    }

    // ── Parallel ────────────────────────────────────────────────────────

    /**
     * 开始构建 Parallel Workflow。
     */
    public static <I> ParallelBuilder<I> parallel() {
        return new ParallelBuilder<>();
    }

    public static final class ParallelBuilder<I> {
        private final List<WorkflowStep<I, ?>> branches = new ArrayList<>();

        ParallelBuilder() {}

        public ParallelBuilder<I> branch(WorkflowStep<I, ?> step) {
            branches.add(step);
            return this;
        }

        public <O> Workflow<I, O> merge(MergeStrategy<O> merger) {
            return new ParallelWorkflow<>(branches, merger, ErrorPolicy.FAIL_FAST);
        }

        public <O> Workflow<I, O> merge(MergeStrategy<O> merger, ErrorPolicy policy) {
            return new ParallelWorkflow<>(branches, merger, policy);
        }
    }

    // ── Loop ────────────────────────────────────────────────────────────

    /**
     * 开始构建 Loop Workflow。
     */
    public static <I, O> LoopBuilder<I, O> loop() {
        return new LoopBuilder<>();
    }

    public static final class LoopBuilder<I, O> {
        private WorkflowStep<I, O> body;
        private Function<O, I> feedback;
        private Predicate<O> terminationCondition;
        private int maxIterations = 10;

        LoopBuilder() {}

        public LoopBuilder<I, O> body(WorkflowStep<I, O> step) {
            this.body = step;
            return this;
        }

        public LoopBuilder<I, O> inputFromOutput(Function<O, I> feedback) {
            this.feedback = feedback;
            return this;
        }

        public LoopBuilder<I, O> until(Predicate<O> condition) {
            this.terminationCondition = condition;
            return this;
        }

        public LoopBuilder<I, O> maxIterations(int max) {
            this.maxIterations = max;
            return this;
        }

        public Workflow<I, O> build() {
            Objects.requireNonNull(body, "Loop body is required");
            Objects.requireNonNull(feedback, "Feedback function is required");
            Objects.requireNonNull(terminationCondition, "Termination condition is required");
            return new LoopWorkflow<>(body, feedback, terminationCondition, maxIterations);
        }
    }

    // ── Conditional ─────────────────────────────────────────────────────

    /**
     * 开始构建 Conditional Workflow。
     */
    public static <I> ConditionalWhenBuilder<I> conditional() {
        return new ConditionalWhenBuilder<>();
    }

    public static final class ConditionalWhenBuilder<I> {
        ConditionalWhenBuilder() {}

        public <O> ConditionalThenBuilder<I, O> when(Predicate<I> predicate) {
            return new ConditionalThenBuilder<>(predicate);
        }
    }

    public static final class ConditionalThenBuilder<I, O> {
        private final Predicate<I> predicate;

        ConditionalThenBuilder(Predicate<I> predicate) {
            this.predicate = predicate;
        }

        public ConditionalOtherwiseBuilder<I, O> then(WorkflowStep<I, O> thenBranch) {
            return new ConditionalOtherwiseBuilder<>(predicate, thenBranch);
        }
    }

    public static final class ConditionalOtherwiseBuilder<I, O> {
        private final Predicate<I> predicate;
        private final WorkflowStep<I, O> thenBranch;

        ConditionalOtherwiseBuilder(Predicate<I> predicate, WorkflowStep<I, O> thenBranch) {
            this.predicate = predicate;
            this.thenBranch = thenBranch;
        }

        public Workflow<I, O> otherwise(WorkflowStep<I, O> otherwiseBranch) {
            return new ConditionalWorkflow<>(predicate, thenBranch, otherwiseBranch);
        }
    }

    // ── Router ──────────────────────────────────────────────────────────

    /**
     * 开始构建 Router Workflow。
     */
    public static <I> RouterBuilder<I> router(Function<I, String> routeFunction) {
        return new RouterBuilder<>(routeFunction);
    }

    public static final class RouterBuilder<I> {
        private final Function<I, String> routeFunction;
        private final Map<String, WorkflowStep<I, ?>> routes = new LinkedHashMap<>();
        private WorkflowStep<I, ?> defaultRoute;

        RouterBuilder(Function<I, String> routeFunction) {
            this.routeFunction = routeFunction;
        }

        @SuppressWarnings("unchecked")
        public <O> RouterBuilder<I> route(String key, WorkflowStep<I, O> step) {
            routes.put(key, step);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <O> RouterBuilder<I> defaultRoute(WorkflowStep<I, O> step) {
            this.defaultRoute = step;
            return this;
        }

        @SuppressWarnings("unchecked")
        public <O> Workflow<I, O> build() {
            Map<String, WorkflowStep<I, O>> typedRoutes = new LinkedHashMap<>();
            for (var entry : routes.entrySet()) {
                typedRoutes.put(entry.getKey(), (WorkflowStep<I, O>) entry.getValue());
            }
            return new RouterWorkflow<>(routeFunction, typedRoutes, (WorkflowStep<I, O>) defaultRoute);
        }
    }

    // ── DAG ─────────────────────────────────────────────────────────────

    /**
     * 开始构建 DAG Workflow。
     */
    public static <I, O> DagBuilder<I, O> dag() {
        return new DagBuilder<>();
    }

    public static final class DagBuilder<I, O> {
        private final Map<String, DagNode> nodes = new LinkedHashMap<>();
        private final List<DagEdge> edges = new ArrayList<>();
        private String outputNodeId;

        DagBuilder() {}

        @SuppressWarnings("unchecked")
        public DagBuilder<I, O> addNode(String id, WorkflowStep<?, ?> step) {
            nodes.put(id, new DagNode(id, (WorkflowStep<Object, Object>) step));
            return this;
        }

        public DagBuilder<I, O> connect(String from, String to) {
            edges.add(new DagEdge(from, to));
            return this;
        }

        public DagBuilder<I, O> output(String nodeId) {
            this.outputNodeId = nodeId;
            return this;
        }

        public Workflow<I, O> build() {
            Objects.requireNonNull(outputNodeId, "Output node is required (call .output(nodeId))");
            if (!nodes.containsKey(outputNodeId)) {
                throw new IllegalArgumentException("Output node '" + outputNodeId + "' not found in nodes");
            }
            return new DagWorkflow<>(nodes, edges, outputNodeId);
        }
    }
}
