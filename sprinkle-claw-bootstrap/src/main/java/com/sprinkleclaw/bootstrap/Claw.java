package com.sprinkleclaw.bootstrap;

import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.AgentLoop;
import com.sprinkleclaw.core.session.SessionId;
import com.sprinkleclaw.core.session.SessionManager;
import com.sprinkleclaw.core.session.SessionStore;
import com.sprinkleclaw.protocol.message.Message;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 运行时门面，由 {@link ClawBuilder} 构建。
 *
 * <h3>MVP2 多轮对话支持</h3>
 * <ul>
 *   <li>{@link #run(String)} — 独立任务，清空上下文后执行</li>
 *   <li>{@link #chat(String)} — 多轮对话，在已有上下文基础上追加执行</li>
 *   <li>{@link #resume(String, String)} — 恢复会话后继续对话</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class Claw {

    private final AgentLoop agentLoop;
    private final AgentContext context;
    private final SessionManager sessionManager;

    Claw(AgentLoop agentLoop, AgentContext context, SessionManager sessionManager) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.context = Objects.requireNonNull(context);
        this.sessionManager = sessionManager;
    }

    /**
     * 兼容 MVP1 的构造方式（无会话管理）。
     */
    Claw(AgentLoop agentLoop, AgentContext context) {
        this(agentLoop, context, null);
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
     * 在已有对话上下文中继续运行（多轮对话）。
     *
     * <p>与 {@link #run(String)} 的区别：不清空上下文，
     * 将新消息追加到已有对话历史中继续执行。</p>
     *
     * @param userMessage 用户输入消息
     * @return Agent 执行结果
     */
    public AgentResult chat(String userMessage) {
        // 首次 chat 自动创建会话
        if (sessionManager != null && context.sessionId() == null) {
            sessionManager.createSession(context);
        }
        context.addMessage(Message.UserMessage.of(userMessage));
        return agentLoop.run();
    }

    /**
     * 恢复会话并继续运行。
     *
     * @param sessionId   要恢复的会话 ID
     * @param userMessage 新的用户消息
     * @return Agent 执行结果
     * @throws IllegalStateException 若未启用会话管理
     */
    public AgentResult resume(String sessionId, String userMessage) {
        if (sessionManager == null) {
            throw new IllegalStateException("Session management not enabled. "
                    + "Use ClawBuilder.sessionStore() to enable.");
        }
        // 恢复消息历史到当前上下文
        var snapshot = sessionManager.restoreSession(
                SessionId.of(sessionId), context.toolDefinitions());
        context.replaceMessages(snapshot.messages());
        context.setSessionId(sessionId);

        // 追加新用户消息并执行
        context.addMessage(Message.UserMessage.of(userMessage));
        return agentLoop.run();
    }

    /**
     * 获取当前会话 ID（如果已创建）。
     *
     * @return 会话 ID，未创建时返回 empty
     */
    public Optional<String> sessionId() {
        return Optional.ofNullable(context.sessionId());
    }

    /**
     * 列出所有保存的会话。
     *
     * @return 会话摘要列表
     * @throws IllegalStateException 若未启用会话管理
     */
    public List<SessionStore.SessionSummary> listSessions() {
        if (sessionManager == null) {
            throw new IllegalStateException("Session management not enabled.");
        }
        return sessionManager.listSessions();
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
