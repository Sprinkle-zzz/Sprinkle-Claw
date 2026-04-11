package com.sprinkleclaw.tool.annotation;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import com.sprinkleclaw.tool.AgentTool;
import com.sprinkleclaw.tool.RiskLevel;
import com.sprinkleclaw.tool.ToolContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link Tool} 标注的方法适配为 {@link AgentTool} 实例。
 * <p>通过反射调用原始方法，并自动处理参数映射和类型转换。</p>
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public final class AnnotatedToolAdapter implements AgentTool {

    private final Object instance;
    private final Method method;
    private final ToolDefinition definition;
    private final boolean readOnly;
    private final boolean concurrencySafe;
    private final RiskLevel risk;

    private AnnotatedToolAdapter(Object instance, Method method, ToolDefinition definition,
                                 boolean readOnly, boolean concurrencySafe, RiskLevel risk) {
        this.instance = instance;
        this.method = method;
        this.definition = definition;
        this.readOnly = readOnly;
        this.concurrencySafe = concurrencySafe;
        this.risk = risk;
    }

    /**
     * 扫描对象的所有方法，将带有 {@link Tool} 注解的方法包装为 {@link AgentTool} 列表。
     *
     * @param toolHolder 包含 {@link Tool} 标注方法的对象
     * @return 适配后的工具列表
     */
    public static List<AgentTool> from(Object toolHolder) {
        List<AgentTool> tools = new ArrayList<>();
        for (Method method : toolHolder.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation != null) {
                method.setAccessible(true);
                String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
                Map<String, Object> schema = SchemaGenerator.generateSchema(method);
                ToolDefinition def = ToolDefinition.of(name, annotation.description(), schema);
                tools.add(new AnnotatedToolAdapter(toolHolder, method, def,
                        annotation.readOnly(), annotation.concurrencySafe(), annotation.riskLevel()));
            }
        }
        return tools;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    /**
     * 通过反射调用原始方法执行工具逻辑。
     * <p>将 LLM 提供的 Map 参数按方法签名顺序解析，并进行类型强制转换。</p>
     */
    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        try {
            Object[] args = resolveArgs(input);
            Object result = method.invoke(instance, args);
            return ToolResult.success(name(), result != null ? result.toString() : "");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ToolResult.error(name(), cause.getMessage());
        }
    }

    /**
     * 根据方法参数列表从 input Map 中提取并转换参数值。
     */
    private Object[] resolveArgs(Map<String, Object> input) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            ToolParam toolParam = params[i].getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.name().isEmpty()) {
                paramName = toolParam.name();
            }
            Object value = input.get(paramName);
            args[i] = coerce(value, params[i].getType());
        }
        return args;
    }

    /**
     * 将值强制转换为目标类型。
     */
    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            return defaultValue(targetType);
        }
        if (targetType.isInstance(value)) {
            return value;
        }

        String str = value.toString();
        if (targetType == String.class) {
            return str;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(str);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(str);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(str);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(str);
        }
        return value;
    }

    /**
     * 获取基本类型的默认值。
     */
    private static Object defaultValue(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public boolean isConcurrencySafe() {
        return concurrencySafe;
    }

    @Override
    public RiskLevel riskLevel() {
        return risk;
    }
}
