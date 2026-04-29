package icu.sprinkle.loom.tool.error;

import java.util.Map;

/**
 * 工具错误处理 SPI，定义工具执行异常时的恢复策略。
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public interface ToolErrorHandler {

    /**
     * 处理工具执行异常并返回恢复策略。
     *
     * @param toolName 工具名称
     * @param input    工具输入参数
     * @param error    发生的异常
     * @return 恢复策略
     */
    ErrorRecovery handle(String toolName, Map<String, Object> input, Throwable error);

    /**
     * 工具错误恢复策略（密封接口）。
     */
    sealed interface ErrorRecovery {
        /**
         * 重试执行。
         */
        record Retry(int maxRetries) implements ErrorRecovery {
        }

        /**
         * 使用替代结果（不重试）。
         */
        record UseResult(String result) implements ErrorRecovery {
        }

        /**
         * 将错误向上传播。
         */
        record Propagate() implements ErrorRecovery {
        }
    }
}
