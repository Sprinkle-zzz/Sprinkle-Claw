package com.sprinkleclaw.workflow.orchestration.conditional;

import java.util.function.Predicate;

/**
 * 条件分支谓词。
 *
 * @param <I> 输入类型
 *
 * @author sprinkle
 * @since 2026/4/12
 */
@FunctionalInterface
public interface BranchPredicate<I> extends Predicate<I> {
}
