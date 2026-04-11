package com.sprinkleclaw.tool;

/**
 * 工具风险等级。
 * <p>影响 {@link ToolPolicy} 的默认决策和审计日志级别。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public enum RiskLevel {

    /**
     * 无副作用操作：读文件、查询、搜索。
     */
    LOW,

    /**
     * 有副作用但可恢复：写文件（有快照）、创建分支。
     */
    MEDIUM,

    /**
     * 不可逆操作：删除文件、执行 bash 命令、push 代码。
     */
    HIGH
}
