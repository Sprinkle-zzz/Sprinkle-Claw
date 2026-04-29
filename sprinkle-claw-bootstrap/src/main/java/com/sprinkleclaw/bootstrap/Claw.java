package com.sprinkleclaw.bootstrap;

import com.sprinkleclaw.api.Experimental;
import com.sprinkleclaw.api.Stable;
import com.sprinkleclaw.core.AgentResult;
import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.AgentLoop;
import com.sprinkleclaw.core.loop.event.AgentEvent;
import com.sprinkleclaw.core.session.SessionId;
import com.sprinkleclaw.core.session.SessionManager;
import com.sprinkleclaw.core.session.SessionStore;
import com.sprinkleclaw.protocol.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

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
@Stable
public final class Claw implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Claw.class);

    private final AgentLoop agentLoop;
    private final AgentContext context;
    private final SessionManager sessionManager;
    private final List<AutoCloseable> resources;
    private final AtomicBoolean running = new AtomicBoolean(false);

    Claw(AgentLoop agentLoop, AgentContext context, SessionManager sessionManager,
         List<AutoCloseable> resources) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.context = Objects.requireNonNull(context);
        this.sessionManager = sessionManager;
        this.resources = resources != null ? List.copyOf(resources) : List.of();
    }

    Claw(AgentLoop agentLoop, AgentContext context, SessionManager sessionManager) {
        this(agentLoop, context, sessionManager, List.of());
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

    // ========== MVP8: 异步 API ==========

    /**
     * 异步执行 Agent Loop。
     *
     * <p>在 Virtual Thread 中运行，不阻塞调用线程。
     * 适用于 Servlet/WebFlux/Vert.x 等非阻塞框架。</p>
     *
     * <p><b>线程安全</b>：同一 Claw 实例不可并发调用 run/chat/resume（同步或异步）。
     * 调用方必须等待前一个 Future 完成后再发起下一个调用。</p>
     *
     * @param userMessage 用户输入消息
     * @return 异步结果
     */
    public CompletableFuture<AgentResult> runAsync(String userMessage) {
        return executeAsync(() -> run(userMessage));
    }

    /**
     * 异步多轮对话。
     *
     * <p><b>线程安全</b>：见 {@link #runAsync(String)}。</p>
     *
     * @param userMessage 用户输入消息
     * @return 异步结果
     */
    public CompletableFuture<AgentResult> chatAsync(String userMessage) {
        return executeAsync(() -> chat(userMessage));
    }

    /**
     * 异步恢复会话。
     *
     * <p><b>线程安全</b>：见 {@link #runAsync(String)}。</p>
     *
     * @param sessionId   要恢复的会话 ID
     * @param userMessage 新的用户消息
     * @return 异步结果
     * @throws IllegalStateException 若未启用会话管理
     */
    public CompletableFuture<AgentResult> resumeAsync(String sessionId, String userMessage) {
        return executeAsync(() -> resume(sessionId, userMessage));
    }

    private static final ExecutorService ASYNC_EXECUTOR =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("claw-async-", 0).factory());

    private CompletableFuture<AgentResult> executeAsync(java.util.function.Supplier<AgentResult> task) {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Another run/chat/resume is already in progress on this Claw instance."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } finally {
                running.set(false);
            }
        }, ASYNC_EXECUTOR);
    }

    // ========== MVP9: Streaming API ==========

    /**
     * 流式执行 Agent Loop，返回 {@link Flow.Publisher} 逐步推送 {@link AgentEvent}。
     *
     * <p>事件类型见 {@code AgentEvent} sealed interface（LlmToken / ToolStart / IterationComplete /
     * AgentComplete 等 17 种）。订阅者必须立即订阅以避免错过 hot publisher 的早期事件。</p>
     *
     * <p><b>线程安全</b>：与 {@code run/chat/resume} / async 共享并发守卫。
     * 守卫在流式循环结束（含异常）时自动释放——订阅者无需手动 close。</p>
     *
     * @param userMessage 用户输入消息
     * @return 事件发布者
     */
    @Experimental("MVP9 引入，订阅时序与并发守卫策略可能在 MVP10 调整")
    public Flow.Publisher<AgentEvent> runStreaming(String userMessage) {
        return executeStreaming(() -> context.addMessage(Message.UserMessage.of(userMessage)));
    }

    /**
     * 流式多轮对话（在已有上下文基础上追加执行）。
     *
     * <p><b>线程安全</b>：见 {@link #runStreaming(String)}。</p>
     *
     * @param userMessage 用户输入消息
     * @return 事件发布者
     */
    @Experimental
    public Flow.Publisher<AgentEvent> chatStreaming(String userMessage) {
        return executeStreaming(() -> {
            if (sessionManager != null && context.sessionId() == null) {
                sessionManager.createSession(context);
            }
            context.addMessage(Message.UserMessage.of(userMessage));
        });
    }

    /**
     * 流式恢复会话并继续运行。
     *
     * <p><b>线程安全</b>：见 {@link #runStreaming(String)}。</p>
     *
     * @param sessionId   要恢复的会话 ID
     * @param userMessage 新的用户消息
     * @return 事件发布者
     * @throws IllegalStateException 若未启用会话管理（同步抛出，不会进入 publisher）
     */
    @Experimental
    public Flow.Publisher<AgentEvent> resumeStreaming(String sessionId, String userMessage) {
        if (sessionManager == null) {
            throw new IllegalStateException("Session management not enabled. "
                    + "Use ClawBuilder.sessionStore() to enable.");
        }
        return executeStreaming(() -> {
            var snapshot = sessionManager.restoreSession(
                    SessionId.of(sessionId), context.toolDefinitions());
            context.replaceMessages(snapshot.messages());
            context.setSessionId(sessionId);
            context.addMessage(Message.UserMessage.of(userMessage));
        });
    }

    private Flow.Publisher<AgentEvent> executeStreaming(Runnable contextSetup) {
        if (!running.compareAndSet(false, true)) {
            return errorPublisher(new IllegalStateException(
                    "Another run/chat/resume is already in progress on this Claw instance."));
        }
        try {
            contextSetup.run();
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return agentLoop.runStreaming(() -> running.set(false));
    }

    /**
     * 创建一个 lazy publisher：订阅者首次 request 时触发 {@code onError} 并完成。
     * 不依赖 SubmissionPublisher 的 hot 语义（hot publisher 在订阅前 submit 会丢事件）。
     */
    private static Flow.Publisher<AgentEvent> errorPublisher(Throwable error) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private volatile boolean delivered = false;

            @Override
            public void request(long n) {
                if (!delivered) {
                    delivered = true;
                    subscriber.onError(error);
                }
            }

            @Override
            public void cancel() {
                delivered = true;
            }
        });
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

    /**
     * 释放所有由 ClawBuilder 在构建期分配的资源（如 MCP 服务器连接）。
     */
    @Override
    public void close() {
        for (AutoCloseable r : resources) {
            try {
                r.close();
            } catch (Exception e) {
                log.warn("Claw 资源释放失败 {}: {}", r.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
