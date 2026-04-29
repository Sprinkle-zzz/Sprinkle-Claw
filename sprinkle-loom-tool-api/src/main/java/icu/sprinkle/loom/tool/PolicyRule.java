package icu.sprinkle.loom.tool;

/**
 * 工具安全策略规则：{@code (permission, pattern) → decision}。
 *
 * <p>{@code permission} 使用 glob 模式匹配工具名称，
 * {@code pattern} 使用 glob 模式匹配工具参数（如文件路径、命令内容）。</p>
 *
 * @param permission 工具名称的 glob 匹配模式（如 {@code "read_file"}、{@code "*"}）
 * @param pattern    工具参数的 glob 匹配模式（如 {@code "*.env"}、{@code "rm -rf /*"}）
 * @param decision   匹配后的决策
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public record PolicyRule(String permission, String pattern, ToolPolicy.Decision decision) {
}
