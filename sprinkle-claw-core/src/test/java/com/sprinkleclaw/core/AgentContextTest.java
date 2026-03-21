package com.sprinkleclaw.core;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.protocol.message.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author sprinkle
 * @since 2026/3/21
 */
class AgentContextTest {

    @Test
    void messages_returnsDefensiveCopy() {
        AgentContext ctx = new AgentContext("prompt", AgentConfig.DEFAULT, List.of());
        ctx.addMessage(Message.UserMessage.of("hello"));

        var snapshot = ctx.messages();
        ctx.addMessage(Message.UserMessage.of("world"));

        assertThat(snapshot).hasSize(1);
        assertThat(ctx.messages()).hasSize(2);
    }

    @Test
    void attributes_threadSafe() {
        AgentContext ctx = new AgentContext("prompt", AgentConfig.DEFAULT, List.of());
        ctx.setAttribute("key", "value");
        assertThat((String) ctx.getAttribute("key")).isEqualTo("value");
    }

    @Test
    void reminders_appendedToSystemPrompt() {
        AgentContext ctx = new AgentContext("base prompt", AgentConfig.DEFAULT, List.of());
        assertThat(ctx.effectiveSystemPrompt()).isEqualTo("base prompt");

        ctx.addReminder("do not forget X");
        assertThat(ctx.effectiveSystemPrompt()).contains("do not forget X");
        assertThat(ctx.effectiveSystemPrompt()).contains("base prompt");
    }
}
