package icu.sprinkle.loom.core.session;

import icu.sprinkle.loom.core.AgentConfig;
import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 会话生命周期管理器。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>管理会话的创建、保存、恢复、删除</li>
 *   <li>实现自动保存逻辑（每 N 轮保存一次）</li>
 *   <li>从 {@link SessionSnapshot} 恢复 {@link AgentContext}</li>
 *   <li>对外提供统一的会话操作接口</li>
 * </ul>
 *
 * <h3>自动保存时机</h3>
 * <ul>
 *   <li>每 {@code autoSaveInterval} 轮保存一次</li>
 *   <li>压缩后立即保存</li>
 *   <li>AgentLoop 结束时最终保存</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public final class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final SessionStore store;
    private final int autoSaveInterval;

    /**
     * 创建会话管理器。
     *
     * @param store            会话存储后端
     * @param autoSaveInterval 自动保存间隔（每 N 轮，0 禁用）
     */
    public SessionManager(SessionStore store, int autoSaveInterval) {
        this.store = store;
        this.autoSaveInterval = autoSaveInterval;
    }

    /**
     * 创建新会话，生成 sessionId 并设置到 AgentContext。
     *
     * @param context 当前 Agent 上下文
     * @return 生成的 sessionId
     */
    public SessionId createSession(AgentContext context) {
        SessionId sessionId = SessionId.generate();
        context.setSessionId(sessionId.value());
        log.info("创建新会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 从持久化存储恢复会话到新的 AgentContext。
     *
     * <p>恢复流程：</p>
     * <ol>
     *   <li>从 SessionStore 加载快照</li>
     *   <li>使用快照中的 systemPrompt 和 config 创建新的 AgentContext</li>
     *   <li>恢复消息历史、元数据、压缩计数</li>
     *   <li>toolDefinitions 使用当前运行时的注册表（不从快照恢复）</li>
     * </ol>
     *
     * @param sessionId       会话标识
     * @param toolDefinitions 当前运行时的工具定义列表
     * @return 恢复后的 AgentContext
     * @throws SessionStoreException 会话不存在或恢复失败
     */
    public AgentContext restoreSession(SessionId sessionId, List<ToolDefinition> toolDefinitions) {
        SessionSnapshot snapshot = store.load(sessionId)
                .orElseThrow(() -> new SessionStoreException(
                        "Session not found: " + sessionId));

        AgentContext context = new AgentContext(
                snapshot.systemPrompt(), snapshot.config(), toolDefinitions);

        // 恢复 sessionId
        context.setSessionId(sessionId.value());

        // 恢复消息历史
        for (Message msg : snapshot.messages()) {
            context.addMessage(msg);
        }

        // 恢复元数据
        for (Map.Entry<String, Object> entry : snapshot.metadata().entrySet()) {
            context.setAttribute(entry.getKey(), entry.getValue());
        }

        // 恢复压缩计数（通过连续调用 recordCompaction）
        for (int i = 0; i < snapshot.compactionCount(); i++) {
            context.recordCompaction();
        }

        log.info("恢复会话: {}（{} 条消息，{} 次压缩）",
                sessionId, snapshot.messages().size(), snapshot.compactionCount());

        return context;
    }

    /**
     * 手动保存当前会话。
     *
     * @param context 当前上下文
     */
    public void save(AgentContext context) {
        if (context.sessionId() == null) {
            log.debug("未设置 sessionId，跳过保存");
            return;
        }
        try {
            SessionSnapshot snapshot = SessionSnapshot.from(context);
            store.save(snapshot);
        } catch (Exception e) {
            log.warn("保存会话失败: {}", e.getMessage());
        }
    }

    /**
     * 自动保存检查：每 autoSaveInterval 轮调用一次。
     * 由 AgentLoop 在每轮结束后调用。
     *
     * @param context   当前上下文
     * @param iteration 当前迭代轮次
     */
    public void autoSaveIfNeeded(AgentContext context, int iteration) {
        if (autoSaveInterval <= 0) {
            return;
        }
        if (context.sessionId() == null) {
            return;
        }
        if (iteration % autoSaveInterval != 0) {
            return;
        }

        try {
            save(context);
            log.debug("自动保存会话 {} (迭代 {})", context.sessionId(), iteration);
        } catch (Exception e) {
            log.warn("自动保存失败: {}", e.getMessage());
        }
    }

    /**
     * 删除会话。
     *
     * @param sessionId 会话标识
     * @return 是否成功删除
     */
    public boolean deleteSession(SessionId sessionId) {
        return store.delete(sessionId);
    }

    /**
     * 列出所有会话。
     *
     * @return 按更新时间降序排列的会话摘要列表
     */
    public List<SessionStore.SessionSummary> listSessions() {
        return store.listSessions();
    }
}
