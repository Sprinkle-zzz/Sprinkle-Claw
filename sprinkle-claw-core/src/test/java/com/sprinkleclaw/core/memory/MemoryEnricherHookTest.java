package com.sprinkleclaw.core.memory;

import com.sprinkleclaw.core.AgentConfig;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.protocol.message.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryEnricherHookTest {

    @Test
    void injectsRelevantMemory_onFirstIteration() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User name is Alice"));
        store.record(new MemoryEntry("User prefers dark mode theme"));
        store.record(new MemoryEntry("Weather is sunny"));

        var hook = new MemoryEnricherHook(store, 5);

        var ctx = new AgentContext("system", AgentConfig.DEFAULT, List.of());
        ctx.addMessage(Message.UserMessage.of("Alice dark mode"));

        hook.preLlmCall(ctx, 0);

        assertThat(ctx.reminders()).hasSize(1);
        String reminder = ctx.reminders().get(0);
        assertThat(reminder).contains("Alice");
        assertThat(reminder).contains("dark mode");
        assertThat(reminder).doesNotContain("sunny");
    }

    @Test
    void noInjection_whenNoMatch() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User likes Java"));

        var hook = new MemoryEnricherHook(store, 5);
        var ctx = new AgentContext("system", AgentConfig.DEFAULT, List.of());
        ctx.addMessage(Message.UserMessage.of("What is Python?"));

        hook.preLlmCall(ctx, 0);

        assertThat(ctx.reminders()).isEmpty();
    }

    @Test
    void noInjection_afterFirstIteration() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("User name is Alice"));

        var hook = new MemoryEnricherHook(store, 5);
        var ctx = new AgentContext("system", AgentConfig.DEFAULT, List.of());
        ctx.addMessage(Message.UserMessage.of("What is my name?"));

        hook.preLlmCall(ctx, 2); // iteration > 1

        assertThat(ctx.reminders()).isEmpty();
    }

    @Test
    void respectsTopK() {
        var store = new InMemoryMemoryStore();
        store.record(new MemoryEntry("A: Java"));
        store.record(new MemoryEntry("B: Java streams"));
        store.record(new MemoryEntry("C: Java concurrency"));

        var hook = new MemoryEnricherHook(store, 2); // only top 2
        var ctx = new AgentContext("system", AgentConfig.DEFAULT, List.of());
        ctx.addMessage(Message.UserMessage.of("Java"));

        hook.preLlmCall(ctx, 0);

        // Should only have at most 2 entries
        String reminder = ctx.reminders().get(0);
        long count = reminder.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(count).isLessThanOrEqualTo(2);
    }

    @Test
    void priority_is45() {
        var hook = new MemoryEnricherHook(new InMemoryMemoryStore());
        assertThat(hook.priority()).isEqualTo(45);
    }
}
