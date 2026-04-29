package icu.sprinkle.loom.core.session;

import icu.sprinkle.loom.api.Stable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话持久化 SPI 接口。
 *
 * <h3>实现方需保证</h3>
 * <ol>
 *   <li>save/load 操作的原子性（不返回半写状态）</li>
 *   <li>并发安全（多线程调用 save 不导致数据损坏）</li>
 *   <li>load 返回的 {@link SessionSnapshot} 是完整的、可直接用于恢复的</li>
 * </ol>
 *
 * @author sprinkle
 * @since 2026/3/25
 */
@Stable
public interface SessionStore {

    /**
     * 保存会话快照。如果 sessionId 已存在则覆盖。
     *
     * @param snapshot 会话快照
     * @throws SessionStoreException 存储失败
     */
    void save(SessionSnapshot snapshot);

    /**
     * 加载会话快照。
     *
     * @param sessionId 会话标识
     * @return 会话快照，不存在时返回 empty
     */
    Optional<SessionSnapshot> load(SessionId sessionId);

    /**
     * 删除会话。
     *
     * @param sessionId 会话标识
     * @return 是否成功删除
     */
    boolean delete(SessionId sessionId);

    /**
     * 列出所有会话（按更新时间降序）。
     *
     * @return 会话摘要列表
     */
    List<SessionSummary> listSessions();

    /**
     * 会话摘要（不含完整消息，用于列表展示）。
     *
     * @param sessionId       会话标识
     * @param createdAt       创建时间
     * @param updatedAt       最近更新时间
     * @param messageCount    消息数量
     * @param compactionCount 压缩次数
     */
    record SessionSummary(
            SessionId sessionId,
            Instant createdAt,
            Instant updatedAt,
            int messageCount,
            int compactionCount
    ) {
    }
}
