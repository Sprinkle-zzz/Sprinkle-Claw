package icu.sprinkle.loom.tool;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;

import java.util.Map;

/**
 * Agent 工具 SPI 接口。
 * <p>实现类必须无状态且线程安全，因为 {@code ToolExecutor} 会通过虚拟线程并发执行多个工具。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public interface AgentTool {

    /**
     * 获取工具定义（名称、描述、输入 JSON Schema）。
     *
     * @return 工具定义
     */
    ToolDefinition definition();

    /**
     * 执行工具调用。
     *
     * @param input   LLM 提供的输入参数
     * @param context 工具执行上下文（包含工作目录等信息）
     * @return 工具执行结果
     */
    ToolResult execute(Map<String, Object> input, ToolContext context);

    /**
     * 获取工具名称（默认从 {@link #definition()} 中获取）。
     *
     * @return 工具名称
     */
    default String name() {
        return definition().name();
    }

    // ===== MVP4 行为标记（default 方法，不破坏兼容性） =====

    /**
     * 该工具是否只读（不修改文件系统、数据库等外部状态）。
     * <p>只读工具可以安全地并行执行。默认 false（fail-closed：未声明的工具视为有副作用）。</p>
     *
     * @return 只读返回 true
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * 该工具是否并发安全（多实例并行执行不会互相干扰）。
     * <p>例如：多个 read_file 并行是安全的，但多个 bash 可能不安全。
     * 默认 false（fail-closed）。</p>
     *
     * @return 并发安全返回 true
     */
    default boolean isConcurrencySafe() {
        return false;
    }

    /**
     * 工具的风险等级，影响 {@link ToolPolicy} 的默认决策和审计日志级别。
     *
     * @return 风险等级
     */
    default RiskLevel riskLevel() {
        return RiskLevel.MEDIUM;
    }
}
