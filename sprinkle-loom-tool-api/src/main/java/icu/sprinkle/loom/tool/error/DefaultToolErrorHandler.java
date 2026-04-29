package icu.sprinkle.loom.tool.error;

import java.util.Map;

/**
 * 默认工具错误处理器：将错误信息包装为替代结果返回给 LLM，由 LLM 决定下一步操作。
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class DefaultToolErrorHandler implements ToolErrorHandler {

    @Override
    public ErrorRecovery handle(String toolName, Map<String, Object> input, Throwable error) {
        return new ErrorRecovery.UseResult("Error executing " + toolName + ": " + error.getMessage());
    }
}
