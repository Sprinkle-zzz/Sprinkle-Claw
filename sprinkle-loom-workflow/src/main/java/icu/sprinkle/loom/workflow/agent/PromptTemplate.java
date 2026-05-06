package icu.sprinkle.loom.workflow.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 渲染声明式 Agent 注解中使用的 prompt 模板。
 * <p>
 * 模板变量统一使用 {@code {paramName}} 形式，并在渲染前做严格校验。
 * 这里不复用外部模板引擎，是为了保持 workflow 模块低依赖，同时让错误信息贴近
 * Agent 方法签名，便于定位问题。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
final class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

    private PromptTemplate() {
    }

    /**
     * 渲染模板内容。
     *
     * @param template  prompt 模板
     * @param variables 参数名到参数值的映射
     * @param owner     模板所属方法或注解描述，用于生成错误信息
     * @return 替换变量后的 prompt
     * @throws IllegalArgumentException 模板引用未知变量或重复引用同一变量时抛出
     */
    static String render(String template, Map<String, ?> variables, String owner) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();

        while (matcher.find()) {
            String name = matcher.group(1);
            if (!variables.containsKey(name)) {
                throw new IllegalArgumentException(owner + " references unknown prompt variable: " + name);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException(owner + " references duplicate prompt variable: " + name);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(variables.get(name))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 校验模板变量与方法参数是否严格匹配。
     *
     * @param template       prompt 模板
     * @param parameterNames 方法参数名列表
     * @param owner          模板所属方法或注解描述，用于生成错误信息
     * @throws IllegalArgumentException 模板引用未知变量、重复变量或未使用某些方法参数时抛出
     */
    static void validateParameters(String template, List<String> parameterNames, String owner) {
        Set<String> placeholders = placeholders(template, owner);
        List<String> unknown = placeholders.stream()
                .filter(name -> !parameterNames.contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(owner + " references unknown prompt variable(s): " + String.join(", ", unknown));
        }

        List<String> unused = new ArrayList<>();
        for (String parameterName : parameterNames) {
            if (!placeholders.contains(parameterName)) {
                unused.add(parameterName);
            }
        }
        if (!unused.isEmpty()) {
            throw new IllegalArgumentException(owner + " does not use prompt parameter(s): " + String.join(", ", unused));
        }
    }

    private static Set<String> placeholders(String template, String owner) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        Set<String> placeholders = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!placeholders.add(name)) {
                throw new IllegalArgumentException(owner + " references duplicate prompt variable: " + name);
            }
        }
        return placeholders;
    }
}
