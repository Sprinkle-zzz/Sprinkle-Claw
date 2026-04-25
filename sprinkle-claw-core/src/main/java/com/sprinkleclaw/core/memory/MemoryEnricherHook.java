package com.sprinkleclaw.core.memory;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.LoopHook;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;

import java.util.List;

/**
 * 在每次 LLM 调用前自动检索相关记忆并注入上下文。
 *
 * <p>仅在首轮迭代注入，避免每轮重复检索。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class MemoryEnricherHook implements LoopHook {

    private final MemoryStore memoryStore;
    private final int topK;

    public MemoryEnricherHook(MemoryStore memoryStore, int topK) {
        this.memoryStore = memoryStore;
        this.topK = topK;
    }

    public MemoryEnricherHook(MemoryStore memoryStore) {
        this(memoryStore, 5);
    }

    @Override
    public int priority() {
        return 45;
    }

    @Override
    public void preLlmCall(AgentContext context, int iteration) {
        if (iteration > 1) return;

        String lastUserMessage = extractLastUserMessage(context);
        if (lastUserMessage == null || lastUserMessage.isBlank()) return;

        List<MemoryEntry> memories = memoryStore.retrieve(lastUserMessage, topK);
        if (memories.isEmpty()) return;

        StringBuilder sb = new StringBuilder("<relevant-memories>\n");
        for (MemoryEntry m : memories) {
            sb.append("- ").append(m.content()).append("\n");
        }
        sb.append("</relevant-memories>");

        context.addReminder(sb.toString());
    }

    private String extractLastUserMessage(AgentContext context) {
        var messages = context.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof Message.UserMessage um) {
                for (ContentBlock block : um.content()) {
                    if (block instanceof ContentBlock.TextBlock tb) {
                        return tb.text();
                    }
                }
            }
        }
        return null;
    }
}
