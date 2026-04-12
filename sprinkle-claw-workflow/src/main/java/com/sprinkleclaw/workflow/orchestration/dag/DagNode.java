package com.sprinkleclaw.workflow.orchestration.dag;

import com.sprinkleclaw.workflow.orchestration.WorkflowStep;

/**
 * DAG 节点：包装一个 {@link WorkflowStep} 并关联节点 ID。
 * <p>DAG 内部类型弱化为 Object，通过运行时校验保证正确性。</p>
 *
 * @param id   节点唯一标识
 * @param step 执行步骤（输入为上游节点输出或 List/Map，取决于上游数量）
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public record DagNode(String id, WorkflowStep<Object, Object> step) {
}
