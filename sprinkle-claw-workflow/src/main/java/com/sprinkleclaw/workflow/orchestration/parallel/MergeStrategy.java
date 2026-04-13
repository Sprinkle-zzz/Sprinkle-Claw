package com.sprinkleclaw.workflow.orchestration.parallel;

import java.util.List;

/**
 * 并行分支结果合并策略。
 *
 * @param <O> 合并后的输出类型
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@FunctionalInterface
public interface MergeStrategy<O> {

    /**
     * 将多个并行分支的结果合并为最终输出。
     *
     * @param results 各分支的输出列表（顺序与 branch 注册顺序一致）
     * @return 合并后的结果
     */
    O merge(List<Object> results);
}
