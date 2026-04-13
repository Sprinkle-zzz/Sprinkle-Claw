package com.sprinkleclaw.workflow.orchestration.dag;

/**
 * DAG 边：从 from 节点到 to 节点的依赖关系。
 *
 * @param from 上游节点 ID
 * @param to   下游节点 ID
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public record DagEdge(String from, String to) {
}
