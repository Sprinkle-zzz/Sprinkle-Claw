package icu.sprinkle.loom.workflow.agent;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 动态 system prompt 生成时可读取的调用上下文。
 * <p>
 * 上下文对象只暴露声明式 Agent 类型、被调用方法和参数映射，不持有 LLM Provider
 * 或工具注册表，避免 prompt 生成逻辑反向耦合执行层。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
public record DynamicSystemPromptContext(
        Class<?> agentType,
        Method method,
        Map<String, Object> arguments
) {

    /**
     * 创建动态 system prompt 上下文。
     *
     * @param agentType 声明式 Agent 接口类型
     * @param method 当前调用的方法
     * @param arguments 参数名到参数值的映射
     */
    public DynamicSystemPromptContext {
        arguments = Map.copyOf(arguments);
    }
}
