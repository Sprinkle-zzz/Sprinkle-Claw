package icu.sprinkle.loom.tool;

import java.util.Map;

/**
 * 工具执行安全策略 SPI，在工具执行前进行权限检查。
 * <p>可用于拦截危险命令、限制文件访问路径等场景。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public interface ToolPolicy {

    /**
     * 检查工具调用是否被允许。
     *
     * @param toolName 工具名称
     * @param input    工具输入参数
     * @param context  工具上下文
     * @return 检查决策
     */
    Decision check(String toolName, Map<String, Object> input, ToolContext context);

    /**
     * 策略检查决策。
     */
    enum Decision {
        /**
         * 允许执行
         */
        ALLOW,
        /**
         * 拒绝执行
         */
        DENY,
        /**
         * 需要用户确认
         */
        ASK_USER
    }
}
