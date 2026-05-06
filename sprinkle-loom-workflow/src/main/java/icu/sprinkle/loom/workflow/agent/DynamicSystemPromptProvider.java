package icu.sprinkle.loom.workflow.agent;

/**
 * 在声明式 Agent 方法调用时动态提供 system prompt。
 * <p>
 * 该 SPI 用于按租户、会话、用户或调用参数生成运行时 system prompt。它只在
 * {@link Agent} 和方法级 {@link SystemMessage} 都没有提供 system prompt 时生效，
 * 避免运行时配置意外覆盖代码中显式声明的指令。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
@FunctionalInterface
public interface DynamicSystemPromptProvider {

    /**
     * 生成当前方法调用的 system prompt。
     *
     * @param context 当前声明式 Agent 方法调用上下文
     * @return system prompt；返回 {@code null} 或空白字符串时视为未提供
     */
    String provide(DynamicSystemPromptContext context);
}
