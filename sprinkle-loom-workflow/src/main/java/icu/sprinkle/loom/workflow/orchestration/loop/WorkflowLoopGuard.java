package icu.sprinkle.loom.workflow.orchestration.loop;

import icu.sprinkle.loom.workflow.orchestration.WorkflowException;

import java.util.LinkedList;

/**
 * Loop 防死循环守卫：检测重复输出，避免 terminationCondition 写错导致无限循环。
 * <p>使用输出 hash 的滑动窗口检测停滞。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class WorkflowLoopGuard {

    private static final int DEFAULT_WINDOW = 3;
    private final int windowSize;
    private final LinkedList<Integer> outputHashes = new LinkedList<>();

    public WorkflowLoopGuard() {
        this(DEFAULT_WINDOW);
    }

    public WorkflowLoopGuard(int windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * 检查输出是否停滞（连续 windowSize 次输出相同 hash）。
     */
    public void checkStagnation(Object output) {
        int hash = output != null ? output.hashCode() : 0;
        outputHashes.addLast(hash);
        if (outputHashes.size() > windowSize) {
            outputHashes.removeFirst();
        }
        if (outputHashes.size() == windowSize && outputHashes.stream().distinct().count() == 1) {
            throw new WorkflowException("loop-guard", -1,
                    "Loop stagnation detected: last " + windowSize + " outputs are identical");
        }
    }
}
