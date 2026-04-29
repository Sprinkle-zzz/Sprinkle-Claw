package icu.sprinkle.loom.workflow.orchestration.dag;

import java.util.*;

/**
 * DAG 环检测：DFS + 三色标记。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class CycleDetector {

    private CycleDetector() {}

    private static final int WHITE = 0; // 未访问
    private static final int GRAY = 1;  // 访问中（在栈上）
    private static final int BLACK = 2; // 已完成

    /**
     * 检测环，如有环则抛异常并附循环路径。
     */
    public static void detectOrThrow(Map<String, DagNode> nodes, List<DagEdge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (var node : nodes.keySet()) {
            adjacency.put(node, new ArrayList<>());
        }
        for (var edge : edges) {
            adjacency.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
        }

        Map<String, Integer> color = new HashMap<>();
        for (var node : nodes.keySet()) {
            color.put(node, WHITE);
        }

        Deque<String> path = new ArrayDeque<>();
        for (var node : nodes.keySet()) {
            if (color.get(node) == WHITE) {
                if (hasCycle(node, adjacency, color, path)) {
                    throw new IllegalStateException(
                            "Cycle detected in DAG: " + formatCyclePath(path, node));
                }
            }
        }
    }

    private static boolean hasCycle(String node, Map<String, List<String>> adjacency,
                                    Map<String, Integer> color, Deque<String> path) {
        color.put(node, GRAY);
        path.addLast(node);

        for (String neighbor : adjacency.getOrDefault(node, List.of())) {
            if (color.getOrDefault(neighbor, WHITE) == GRAY) {
                path.addLast(neighbor);
                return true;
            }
            if (color.getOrDefault(neighbor, WHITE) == WHITE) {
                if (hasCycle(neighbor, adjacency, color, path)) {
                    return true;
                }
            }
        }

        path.removeLast();
        color.put(node, BLACK);
        return false;
    }

    private static String formatCyclePath(Deque<String> path, String cycleStart) {
        var sb = new StringBuilder();
        boolean inCycle = false;
        for (String node : path) {
            if (node.equals(cycleStart)) inCycle = true;
            if (inCycle) {
                if (!sb.isEmpty()) sb.append(" → ");
                sb.append(node);
            }
        }
        return sb.toString();
    }
}
