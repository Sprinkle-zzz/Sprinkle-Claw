package icu.sprinkle.loom.workflow.agent;

import icu.sprinkle.loom.llm.LlmProvider;
import icu.sprinkle.loom.tool.AgentTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AgentFactory} 的配置。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public record AgentFactoryConfig(
        LlmProvider llmProvider,
        List<AgentTool> tools,
        Duration timeout,
        DynamicSystemPromptProvider dynamicSystemPromptProvider
) {
    public static Builder defaults() {
        return new Builder();
    }

    public static final class Builder {
        private LlmProvider llmProvider;
        private final List<AgentTool> tools = new ArrayList<>();
        private Duration timeout = Duration.ofSeconds(120);
        private DynamicSystemPromptProvider dynamicSystemPromptProvider;

        private Builder() {}

        public Builder llmProvider(LlmProvider llmProvider) {
            this.llmProvider = llmProvider;
            return this;
        }

        public Builder addTool(AgentTool tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder tools(List<AgentTool> tools) {
            this.tools.addAll(tools);
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 设置运行时 system prompt 提供者。
         * <p>
         * 该提供者只在注解没有声明 system prompt 时生效，适合应用层按租户、
         * 会话或调用参数补充默认角色指令。
         *
         * @param dynamicSystemPromptProvider 运行时 system prompt 提供者，可为 {@code null}
         * @return 当前构建器
         */
        public Builder dynamicSystemPromptProvider(DynamicSystemPromptProvider dynamicSystemPromptProvider) {
            this.dynamicSystemPromptProvider = dynamicSystemPromptProvider;
            return this;
        }

        public AgentFactoryConfig build() {
            if (llmProvider == null) {
                throw new IllegalStateException("llmProvider is required");
            }
            return new AgentFactoryConfig(llmProvider, List.copyOf(tools), timeout, dynamicSystemPromptProvider);
        }
    }
}
