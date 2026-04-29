package icu.sprinkle.loom.protocol.tool;

/**
 * 工具执行结果，用于将工具的输出反馈给 LLM。
 *
 * @param toolCallId 关联的工具调用 ID（工具内部返回时可为空，ToolExecutor 负责填充）
 * @param toolName   工具名称
 * @param output     工具输出内容
 * @param isError    是否为错误结果
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record ToolResult(String toolCallId, String toolName, String output, boolean isError) {

    /**
     * 工具内部使用：创建成功结果（无 callId，由 ToolExecutor 补充）。
     *
     * @param toolName 工具名称
     * @param output   输出内容
     * @return 成功结果
     */
    public static ToolResult success(String toolName, String output) {
        return new ToolResult("", toolName, output, false);
    }

    /**
     * 工具内部使用：创建失败结果（无 callId，由 ToolExecutor 补充）。
     *
     * @param toolName 工具名称
     * @param error    错误信息
     * @return 错误结果
     */
    public static ToolResult error(String toolName, String error) {
        return new ToolResult("", toolName, error, true);
    }

    /**
     * ToolExecutor 使用：基于工具返回结果，填充 callId 生成最终结果。
     *
     * @param toolCallId 工具调用 ID
     * @return 包含 callId 的新 ToolResult
     */
    public ToolResult withCallId(String toolCallId) {
        return new ToolResult(toolCallId, this.toolName, this.output, this.isError);
    }
}
