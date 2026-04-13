package com.sprinkleclaw.workflow.agent;

import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.tool.AgentTool;

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
        Duration timeout
) {
    public static Builder defaults() {
        return new Builder();
    }

    public static final class Builder {
        private LlmProvider llmProvider;
        private final List<AgentTool> tools = new ArrayList<>();
        private Duration timeout = Duration.ofSeconds(120);

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

        public AgentFactoryConfig build() {
            if (llmProvider == null) {
                throw new IllegalStateException("llmProvider is required");
            }
            return new AgentFactoryConfig(llmProvider, List.copyOf(tools), timeout);
        }
    }
}
