package icu.sprinkle.loom.core.loop;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

/**
 * Doom Loop 检测器：识别同一工具以相同参数被连续重复调用的情况。
 *
 * <p>维护一个固定大小的滑动窗口（{@code threshold}），记录最近工具调用的哈希值。
 * 当窗口内所有哈希值相同时，判定为 Doom Loop，返回 {@code true}。</p>
 *
 * <h3>线程安全</h3>
 * <p>本类非线程安全，仅由 AgentLoop 主线程调用。</p>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public final class ToolLoopDetector {

    private final int threshold;
    private final Deque<String> recentCallHashes;

    /**
     * @param threshold 触发 Doom Loop 判定所需的连续重复次数
     */
    public ToolLoopDetector(int threshold) {
        if (threshold < 2) {
            throw new IllegalArgumentException("threshold must be >= 2, got " + threshold);
        }
        this.threshold = threshold;
        this.recentCallHashes = new ArrayDeque<>(threshold + 1);
    }

    /**
     * 记录一次工具调用并检查是否构成 Doom Loop。
     *
     * @param toolName 工具名称
     * @param input    工具输入参数
     * @return 如果检测到 Doom Loop 返回 {@code true}
     */
    public boolean recordAndCheck(String toolName, Map<String, Object> input) {
        String callHash = toolName + ":" + Objects.hash(input);
        recentCallHashes.addLast(callHash);
        while (recentCallHashes.size() > threshold) {
            recentCallHashes.removeFirst();
        }

        if (recentCallHashes.size() < threshold) {
            return false;
        }

        return recentCallHashes.stream().distinct().count() == 1;
    }

    /**
     * 重置检测状态。
     */
    public void reset() {
        recentCallHashes.clear();
    }
}
