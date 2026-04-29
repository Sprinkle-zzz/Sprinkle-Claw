package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.AgentConfig;
import icu.sprinkle.loom.protocol.llm.ChatResponse;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Map;

/**
 * 循环守卫，防止 Agent 进入无限循环。
 *
 * <p>检测四种异常情况：</p>
 * <ul>
 *   <li>超过最大迭代次数</li>
 *   <li>超时</li>
 *   <li>连续重复 LLM 响应</li>
 *   <li>Doom Loop：同一工具以相同参数被连续重复调用</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/19
 */
public final class LoopGuard {

    private final AgentConfig config;
    private final Instant startTime;
    private int consecutiveErrors;
    private final LinkedList<String> recentResponses = new LinkedList<>();
    private final ToolLoopDetector toolLoopDetector;

    public LoopGuard(AgentConfig config) {
        this.config = config;
        this.startTime = Instant.now();
        this.toolLoopDetector = new ToolLoopDetector(config.doomLoopThreshold());
    }

    /**
     * 检查当前迭代是否超限或超时。
     *
     * @param iteration 当前迭代轮次
     * @throws LoopExhaustedException 超过限制时抛出
     */
    public void checkIteration(int iteration) {
        if (iteration > config.maxLoopIterations()) {
            throw new LoopExhaustedException("Max loop iterations reached: " + config.maxLoopIterations());
        }

        var elapsed = java.time.Duration.between(startTime, Instant.now());
        if (elapsed.compareTo(config.loopTimeout()) > 0) {
            throw new LoopExhaustedException("Loop timeout exceeded: " + config.loopTimeout());
        }
    }

    /**
     * 记录 LLM 响应，检测是否出现连续重复。
     *
     * @param response LLM 响应
     * @throws LoopExhaustedException 检测到连续重复时抛出
     */
    public void recordResponse(ChatResponse response) {
        String text = response.textContent();
        if (text == null || text.isBlank()) {
            return;
        }

        int maxRep = config.maxRepetitions();
        recentResponses.addLast(text);
        if (recentResponses.size() > maxRep + 1) {
            recentResponses.removeFirst();
        }

        if (recentResponses.size() > maxRep) {
            boolean allSame = recentResponses.stream().allMatch(r -> r.equals(text));
            if (allSame) {
                throw new LoopExhaustedException("Detected " + maxRep + " consecutive identical responses");
            }
        }

        consecutiveErrors = 0;
    }

    /**
     * 检测工具调用级的 Doom Loop（同一工具以相同参数被连续重复调用）。
     *
     * @param toolName 工具名称
     * @param input    工具输入参数
     * @return 如果检测到 Doom Loop 返回 {@code true}
     */
    public boolean checkToolLoop(String toolName, Map<String, Object> input) {
        return toolLoopDetector.recordAndCheck(toolName, input);
    }

    /**
     * 记录错误次数，超过阈值时终止循环。
     *
     * @throws LoopExhaustedException 连续错误超限时抛出
     */
    public void recordError() {
        consecutiveErrors++;
        if (consecutiveErrors >= config.maxConsecutiveErrors()) {
            throw new LoopExhaustedException("Max consecutive errors reached: " + config.maxConsecutiveErrors());
        }
    }

    /**
     * 循环耗尽异常，表示 Agent 循环因安全限制而终止。
     */
    public static class LoopExhaustedException extends RuntimeException {
        public LoopExhaustedException(String message) {
            super(message);
        }
    }
}
