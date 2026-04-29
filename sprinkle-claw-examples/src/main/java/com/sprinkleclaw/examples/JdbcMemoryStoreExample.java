package com.sprinkleclaw.examples;

import com.sprinkleclaw.core.memory.MemoryEntry;
import com.sprinkleclaw.core.memory.MemoryStore;

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

/**
 * JDBC 实现的 {@link MemoryStore} 样例。
 *
 * <p>展示如何用 ~80 行 JDBC 代码自实现 {@code MemoryStore} SPI。SDK 不内置
 * {@code sprinkle-claw-memory-jdbc} 模块的原因（参见 {@code 12-MVP8完成后架构审计与迭代规划.md} 8.2）：</p>
 * <ul>
 *   <li>接口仅 5 个方法，自实现成本极低</li>
 *   <li>关键词 LIKE 检索是<b>反模式</b>——生产场景应使用向量数据库（pgvector / Milvus / Weaviate / Qdrant），
 *       具体选型属于业务决策，不该 SDK 替你做</li>
 * </ul>
 *
 * <p>本样例仅作"如何自实现"的参考；生产场景**强烈建议**走向量检索，
 * 见 {@link PgVectorMemoryStoreExample}。</p>
 *
 * <pre>{@code
 * mvn compile exec:java -pl sprinkle-claw-examples -Dexec.mainClass=com.sprinkleclaw.examples.JdbcMemoryStoreExample
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/28
 */
public class JdbcMemoryStoreExample {

    /**
     * 用户可 copy 的实现核心。生产中替换 DataSource 为业务 HikariCP / DBCP 等连接池。
     */
    public static class JdbcMemoryStore implements MemoryStore {

        private final DataSource dataSource;

        public JdbcMemoryStore(DataSource dataSource) {
            this.dataSource = dataSource;
            initSchema();
        }

        private void initSchema() {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                         CREATE TABLE IF NOT EXISTS memory_entries (
                             id VARCHAR(64) PRIMARY KEY,
                             content TEXT NOT NULL,
                             created_at TIMESTAMP NOT NULL
                         )
                         """)) {
                ps.execute();
            } catch (SQLException e) {
                throw new RuntimeException("初始化 memory schema 失败", e);
            }
        }

        @Override
        public void record(MemoryEntry entry) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "MERGE INTO memory_entries (id, content, created_at) KEY(id) VALUES (?, ?, ?)")) {
                ps.setString(1, entry.id());
                ps.setString(2, entry.content());
                ps.setTimestamp(3, Timestamp.from(entry.createdAt()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<MemoryEntry> retrieve(String query, int topK) {
            // 关键词 LIKE 检索 —— 反模式但够用作样例。生产请用向量检索。
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, content, created_at FROM memory_entries "
                                 + "WHERE LOWER(content) LIKE ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, "%" + query.toLowerCase() + "%");
                ps.setInt(2, topK);
                return readEntries(ps.executeQuery());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void delete(String memoryId) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM memory_entries WHERE id = ?")) {
                ps.setString(1, memoryId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<MemoryEntry> listAll() {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, content, created_at FROM memory_entries ORDER BY created_at DESC")) {
                return readEntries(ps.executeQuery());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int size() {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM memory_entries");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        private static List<MemoryEntry> readEntries(ResultSet rs) throws SQLException {
            List<MemoryEntry> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new MemoryEntry(
                        rs.getString("id"),
                        rs.getString("content"),
                        Map.of(),
                        rs.getTimestamp("created_at").toInstant()));
            }
            return result;
        }
    }

    public static void main(String[] args) {
        // 用 H2 内存数据库演示。生产中传入业务 DataSource 即可。
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:memory_demo;DB_CLOSE_DELAY=-1");

        MemoryStore store = new JdbcMemoryStore(ds);

        store.record(new MemoryEntry("用户偏好深色主题"));
        store.record(new MemoryEntry("用户习惯用中文沟通"));
        store.record(new MemoryEntry("用户工作时区是 UTC+8"));

        System.out.println("总记忆数: " + store.size());
        System.out.println("\n检索『中文』:");
        store.retrieve("中文", 5).forEach(e -> System.out.println("  - " + e.content()));

        System.out.println("\n所有记忆（按时间降序）:");
        store.listAll().forEach(e -> System.out.println("  [" + e.createdAt() + "] " + e.content()));
    }

    private JdbcMemoryStoreExample() {
    }
}
