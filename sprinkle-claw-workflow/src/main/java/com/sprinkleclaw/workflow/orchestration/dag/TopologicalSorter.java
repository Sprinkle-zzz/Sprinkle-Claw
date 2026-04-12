package com.sprinkleclaw.workflow.orchestration.dag;

import java.util.*;

/**
 * Kahn 拓扑排序算法：输出层级序列（同层节点可并行执行）。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class TopologicalSorter {

    private TopologicalSorter() {}

    /**
     * 拓扑排序，返回层级列表。每一层内的节点相互独立，可并行执行。
     *
     * @param nodes 节点集合
     * @param edges 边集合
     * @return 层级列表，每层包含可并行执行的节点 ID
     */
    public static List<List<String>> sortIntoLayers(Map<String, DagNode> nodes, List<DagEdge> edges) {
        // 构建入度表和邻接表
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (var nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
            adjacency.put(nodeId, new ArrayList<>());
        }
        for (var edge : edges) {
            adjacency.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            inDegree.merge(edge.to(), 1, Integer::sum);
        }

        // Kahn: 按层输出
        List<List<String>> layers = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            List<String> layer = new ArrayList<>(queue);
            queue.clear();
            layers.add(layer);

            for (String node : layer) {
                for (String neighbor : adjacency.getOrDefault(node, List.of())) {
                    int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return layers;
    }
}
