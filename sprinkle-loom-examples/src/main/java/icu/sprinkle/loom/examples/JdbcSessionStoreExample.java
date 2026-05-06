package icu.sprinkle.loom.examples;

import icu.sprinkle.loom.core.AgentConfig;
import icu.sprinkle.loom.core.session.SessionId;
import icu.sprinkle.loom.core.session.SessionSnapshot;
import icu.sprinkle.loom.core.session.SessionSnapshotSerializer;
import icu.sprinkle.loom.core.session.SessionStore;
import icu.sprinkle.loom.core.session.SessionStoreException;
import icu.sprinkle.loom.protocol.message.Message;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC 实现的 {@link SessionStore} 样例。
 * <p>
 * 展示用户如何基于 {@link SessionSnapshotSerializer} 自实现会话持久化。
 * SDK 保留 {@code SessionStore} SPI 和序列化工具，不内置 JDBC/Redis 模块，
 * 是因为生产会话存储通常需要结合业务的租户隔离、加密、TTL、归档和审计策略。
 *
 * <pre>{@code
 * mvn compile exec:java -pl sprinkle-loom-examples -Dexec.mainClass=icu.sprinkle.loom.examples.JdbcSessionStoreExample
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/5/6
 */
public class JdbcSessionStoreExample {

    /**
     * 用户可参考的 JDBC SessionStore 核心实现。
     * <p>
     * 为了让样例短小清晰，完整快照以 JSON 形式写入单表。生产环境可以根据检索、
     * 审计或归档需求拆分元数据列和消息明细表。
     */
    public static class JdbcSessionStore implements SessionStore {

        private final DataSource dataSource;

        public JdbcSessionStore(DataSource dataSource) {
            this.dataSource = dataSource;
            initSchema();
        }

        private void initSchema() {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                         CREATE TABLE IF NOT EXISTS session_snapshots (
                             session_id VARCHAR(64) PRIMARY KEY,
                             snapshot_json CLOB NOT NULL,
                             created_at TIMESTAMP NOT NULL,
                             updated_at TIMESTAMP NOT NULL,
                             message_count INT NOT NULL,
                             compaction_count INT NOT NULL
                         )
                         """)) {
                ps.execute();
            } catch (SQLException e) {
                throw new SessionStoreException("初始化 session schema 失败", e);
            }
        }

        @Override
        public void save(SessionSnapshot snapshot) {
            String json = SessionSnapshotSerializer.serialize(snapshot);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                         MERGE INTO session_snapshots
                             (session_id, snapshot_json, created_at, updated_at, message_count, compaction_count)
                         KEY(session_id)
                         VALUES (?, ?, ?, ?, ?, ?)
                         """)) {
                ps.setString(1, snapshot.sessionId().value());
                ps.setString(2, json);
                ps.setTimestamp(3, Timestamp.from(snapshot.createdAt()));
                ps.setTimestamp(4, Timestamp.from(snapshot.updatedAt()));
                ps.setInt(5, snapshot.messages().size());
                ps.setInt(6, snapshot.compactionCount());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new SessionStoreException("保存 session 失败: " + snapshot.sessionId(), e);
            }
        }

        @Override
        public Optional<SessionSnapshot> load(SessionId sessionId) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT snapshot_json FROM session_snapshots WHERE session_id = ?")) {
                ps.setString(1, sessionId.value());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(SessionSnapshotSerializer.deserialize(rs.getString("snapshot_json")));
                }
            } catch (SQLException e) {
                throw new SessionStoreException("读取 session 失败: " + sessionId, e);
            }
        }

        @Override
        public boolean delete(SessionId sessionId) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM session_snapshots WHERE session_id = ?")) {
                ps.setString(1, sessionId.value());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new SessionStoreException("删除 session 失败: " + sessionId, e);
            }
        }

        @Override
        public List<SessionSummary> listSessions() {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                         SELECT session_id, created_at, updated_at, message_count, compaction_count
                         FROM session_snapshots
                         ORDER BY updated_at DESC
                         """)) {
                List<SessionSummary> summaries = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        summaries.add(new SessionSummary(
                                SessionId.of(rs.getString("session_id")),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant(),
                                rs.getInt("message_count"),
                                rs.getInt("compaction_count")));
                    }
                }
                return summaries;
            } catch (SQLException e) {
                throw new SessionStoreException("列出 session 失败", e);
            }
        }
    }

    public static void main(String[] args) {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:session_demo;DB_CLOSE_DELAY=-1");

        SessionStore store = new JdbcSessionStore(ds);
        SessionId sessionId = SessionId.generate();
        Instant now = Instant.now();
        SessionSnapshot snapshot = new SessionSnapshot(
                sessionId,
                List.of(Message.UserMessage.of("帮我重构这个 Java Agent SDK")),
                Map.of("tenant", "demo"),
                "你是一个务实的 Java SDK 助手",
                AgentConfig.DEFAULT,
                now,
                now,
                0);

        store.save(snapshot);

        System.out.println("已保存 session: " + sessionId);
        System.out.println("会话数量: " + store.listSessions().size());
        store.load(sessionId).ifPresent(loaded ->
                System.out.println("恢复消息数: " + loaded.messages().size()));
    }

    private JdbcSessionStoreExample() {
    }
}
