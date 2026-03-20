package com.sprinkleclaw.bootstrap;

import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.AgentLoop;
import com.sprinkleclaw.protocol.message.Message;

import java.util.Objects;

/**
 * Agent 运行时门面，由 {@link ClawBuilder} 构建。
 * <p>构建后不可变，提供 {@link #run(String)} 方法发起对话。</p>
 *
 * <pre>
 * Claw agent = ClawBuilder.create()
 *     .apiKey("sk-...")
 *     .model("deepseek-chat")
 *     .build();
 *
 * AgentResult result = agent.run("请帮我创建一个 Hello World 程序");
 * </pre>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class Claw {

    private final AgentLoop agentLoop;
    private final AgentContext context;

    Claw(AgentLoop agentLoop, AgentContext context) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.context = Objects.requireNonNull(context);
    }

    /**
     * 以用户消息启动 Agent 循环并返回结果。
     *
     * @param userMessage 用户输入消息
     * @return Agent 执行结果
     */
    public AgentResult run(String userMessage) {
        context.addMessage(Message.UserMessage.of(userMessage));
        return agentLoop.run();
    }

    /**
     * 获取底层 Agent 上下文（可用于检查对话历史、设置属性等）。
     *
     * @return Agent 上下文
     */
    public AgentContext context() {
        return context;
    }
}
