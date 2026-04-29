package com.sprinkleclaw.examples;

import com.sprinkleclaw.core.memory.MemoryEntry;
import com.sprinkleclaw.core.memory.MemoryStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PgVector 向量检索实现的 {@link MemoryStore} 样例（生产推荐方向）。
 *
 * <p>与 {@link JdbcMemoryStoreExample} 对比：</p>
 * <ul>
 *   <li>JDBC 关键词 LIKE 检索是反模式 —— 无法理解语义、无法跨语言、性能差</li>
 *   <li>向量检索通过 embedding model 将记忆和查询都映射到 N 维向量，用余弦相似度 / L2 距离排序，
 *       支持语义近似（"用户喜欢深色" 能匹配查询 "用户主题偏好"）</li>
 * </ul>
 *
 * <p><b>本样例不实际运行</b>——pgvector 需要真实 PostgreSQL 实例 + 已加载 pgvector 扩展。
 * 直接复制本类作为生产实现的起点即可。</p>
 *
 * <h3>前置准备</h3>
 * <pre>{@code
 * -- PostgreSQL 14+
 * CREATE EXTENSION IF NOT EXISTS vector;
 * CREATE TABLE memory_entries (
 *     id VARCHAR(64) PRIMARY KEY,
 *     content TEXT NOT NULL,
 *     embedding vector(1536) NOT NULL,        -- OpenAI text-embedding-3-small 维度
 *     created_at TIMESTAMP NOT NULL
 * );
 * CREATE INDEX ON memory_entries USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/28
 */
public class PgVectorMemoryStoreExample {

    /**
     * Embedding 客户端契约。生产中可对接 OpenAI Embeddings API、本地 ONNX 模型、
     * 或托管服务（DashScope、Voyage、Cohere 等）。
     */
    public interface EmbeddingClient {
        /** 把文本编码为定长向量（如 OpenAI text-embedding-3-small 是 1536 维）。 */
        float[] embed(String text);
    }

    public static class PgVectorMemoryStore implements MemoryStore {

        private final DataSource dataSource;
        private final EmbeddingClient embedding;

        public PgVectorMemoryStore(DataSource dataSource, EmbeddingClient embedding) {
            this.dataSource = dataSource;
            this.embedding = embedding;
        }

        @Override
        public void record(MemoryEntry entry) {
            float[] vec = embedding.embed(entry.content());
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO memory_entries (id, content, embedding, created_at) "
                                 + "VALUES (?, ?, ?::vector, ?) "
                                 + "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, "
                                 + "embedding = EXCLUDED.embedding")) {
                ps.setString(1, entry.id());
                ps.setString(2, entry.content());
                ps.setString(3, formatVector(vec));
                ps.setTimestamp(4, Timestamp.from(entry.createdAt()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<MemoryEntry> retrieve(String query, int topK) {
            float[] queryVec = embedding.embed(query);
            // <-> 是 pgvector 的 L2 距离运算符；<#> 是负内积；<=> 是余弦距离
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, content, created_at FROM memory_entries "
                                 + "ORDER BY embedding <=> ?::vector LIMIT ?")) {
                ps.setString(1, formatVector(queryVec));
                ps.setInt(2, topK);
                return readEntries(ps.executeQuery());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void delete(String memoryId) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM memory_entries WHERE id = ?")) {
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

        /** 把 {@code float[]} 序列化为 pgvector 接受的字符串格式：{@code [0.1,0.2,...]}。 */
        private static String formatVector(float[] vec) {
            StringBuilder sb = new StringBuilder(vec.length * 8);
            sb.append('[');
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(vec[i]);
            }
            sb.append(']');
            return sb.toString();
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

    private PgVectorMemoryStoreExample() {
    }
}
