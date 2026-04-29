package icu.sprinkle.loom.core.loop.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * max_output_tokens 三级升级器。
 * <p>当 LLM 输出因 max_tokens 截断时，逐级升级输出限制：
 * <ol>
 *   <li>升级 max_output_tokens（4096 → 8192 → 16384）</li>
 *   <li>注入恢复提示消息</li>
 *   <li>中止并报错</li>
 * </ol></p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class MaxOutputTokensEscalator {

    private static final Logger log = LoggerFactory.getLogger(MaxOutputTokensEscalator.class);

    private static final int[] ESCALATION_LEVELS = {4096, 8192, 16384};

    private final int maxAttempts;
    private int currentLevel = 0;

    public MaxOutputTokensEscalator(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public MaxOutputTokensEscalator() {
        this(2);
    }

    /**
     * 尝试升级输出限制。
     *
     * @param currentMaxTokens 当前 max_output_tokens
     * @return 升级结果
     */
    public EscalationResult escalate(int currentMaxTokens) {
        currentLevel++;

        if (currentLevel > maxAttempts) {
            return new EscalationResult.Exhausted(
                    "Max output tokens escalation exhausted after " + maxAttempts + " attempts");
        }

        // 找到下一个更大的级别
        int nextTokens = currentMaxTokens;
        for (int level : ESCALATION_LEVELS) {
            if (level > currentMaxTokens) {
                nextTokens = level;
                break;
            }
        }

        if (nextTokens <= currentMaxTokens) {
            // 已到最大级别，注入恢复提示
            if (currentLevel <= maxAttempts) {
                log.info("max_output_tokens 已达最大级别 {}，注入恢复提示", currentMaxTokens);
                return new EscalationResult.InjectHint(currentMaxTokens,
                        "Your previous response was truncated. Please continue from where you left off.");
            }
            return new EscalationResult.Exhausted("Cannot escalate beyond " + currentMaxTokens);
        }

        log.info("升级 max_output_tokens: {} → {}", currentMaxTokens, nextTokens);
        return new EscalationResult.Upgraded(nextTokens);
    }

    /**
     * 重置升级状态。
     */
    public void reset() {
        currentLevel = 0;
    }

    /**
     * 升级结果。
     */
    public sealed interface EscalationResult {
        /**
         * 成功升级到更高的 token 限制
         */
        record Upgraded(int newMaxTokens) implements EscalationResult {
        }

        /**
         * 已到最高级别，注入恢复提示
         */
        record InjectHint(int maxTokens, String hint) implements EscalationResult {
        }

        /**
         * 所有升级手段已耗尽
         */
        record Exhausted(String reason) implements EscalationResult {
        }
    }
}
