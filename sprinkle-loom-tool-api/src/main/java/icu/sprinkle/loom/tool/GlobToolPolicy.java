package icu.sprinkle.loom.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Glob 模式匹配的工具安全策略，采用 Last-match-wins 求值模型。
 *
 * <p>规则列表中最后一个匹配的规则生效，类似 CSS 优先级机制。
 * 这种设计允许先设置宽松默认规则，再用特定规则覆盖。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * GlobToolPolicy policy = new GlobToolPolicy(List.of(
 *     new PolicyRule("*", "*", Decision.ALLOW),           // 默认允许所有
 *     new PolicyRule("read_file", "*.env", Decision.DENY) // 但禁止读取 .env 文件
 * ));
 * </pre>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public final class GlobToolPolicy implements ToolPolicy {

    private final List<PolicyRule> rules;

    public GlobToolPolicy(List<PolicyRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * 使用 Last-match-wins 策略求值：从后往前查找第一个匹配的规则。
     */
    @Override
    public Decision check(String toolName, Map<String, Object> input, ToolContext context) {
        String pattern = extractPattern(toolName, input);

        for (int i = rules.size() - 1; i >= 0; i--) {
            PolicyRule rule = rules.get(i);
            if (globMatch(toolName, rule.permission()) && globMatch(pattern, rule.pattern())) {
                return rule.decision();
            }
        }
        return Decision.ALLOW;
    }

    /**
     * 从工具输入中提取用于策略匹配的关键模式。
     * <p>bash 工具提取 command 参数，文件工具提取 path 参数。</p>
     */
    private static String extractPattern(String toolName, Map<String, Object> input) {
        if ("bash".equals(toolName)) {
            Object cmd = input.get("command");
            return cmd != null ? cmd.toString() : "*";
        }
        Object path = input.get("path");
        return path != null ? path.toString() : "*";
    }

    /**
     * 简单 glob 匹配：{@code *} 匹配任意字符序列，{@code ?} 匹配单个字符。
     */
    static boolean globMatch(String input, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (input == null) {
            return false;
        }

        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return input.matches(regex);
    }

    /**
     * 合并多组规则（后面的规则优先级更高）。
     *
     * @param rulesets 规则集数组
     * @return 合并后的策略
     */
    @SafeVarargs
    public static GlobToolPolicy merge(List<PolicyRule>... rulesets) {
        List<PolicyRule> merged = new ArrayList<>();
        for (List<PolicyRule> ruleset : rulesets) {
            merged.addAll(ruleset);
        }
        return new GlobToolPolicy(merged);
    }

    /**
     * 内置默认规则集：允许所有工具，但拦截敏感文件和危险命令。
     */
    public static List<PolicyRule> defaultRules() {
        return List.of(
                new PolicyRule("*", "*", Decision.ALLOW),
                new PolicyRule("read_file", "*.env", Decision.DENY),
                new PolicyRule("read_file", "*.env.*", Decision.DENY),
                new PolicyRule("read_file", "*.env.example", Decision.ALLOW),
                new PolicyRule("edit_file", "*.env", Decision.DENY),
                new PolicyRule("write_file", "*.env", Decision.DENY),
                new PolicyRule("bash", "rm -rf /*", Decision.DENY),
                new PolicyRule("bash", "sudo *", Decision.ASK_USER),
                new PolicyRule("bash", "shutdown *", Decision.DENY)
        );
    }

    /**
     * 使用默认规则集创建策略实例。
     */
    public static GlobToolPolicy withDefaults() {
        return new GlobToolPolicy(defaultRules());
    }
}
