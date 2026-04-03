package com.sprinkleclaw.core.session;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 基于文件系统的会话持久化实现。
 *
 * <h3>存储结构</h3>
 * <pre>
 * {baseDir}/
 * ├── 20260322-a1b2c3d4.json    ← 单个会话快照
 * ├── 20260322-e5f6g7h8.json
 * └── ...
 * </pre>
 *
 * <h3>原子写入</h3>
 * <p>使用"写临时文件 + 原子 rename"模式，确保进程崩溃不产生半写状态。</p>
 *
 * <h3>listSessions 优化</h3>
 * <p>仅读取 JSON 文件的头部元数据字段（使用 Jackson Streaming API），
 * 不加载完整的 messages 数组。</p>
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public final class FileSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(FileSessionStore.class);

    private final Path baseDir;
    private final ObjectMapper mapper;

    /**
     * 创建文件会话存储。
     *
     * @param baseDir 存储目录
     */
    public FileSessionStore(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = SessionObjectMapper.create();
    }

    @Override
    public void save(SessionSnapshot snapshot) {
        try {
            Files.createDirectories(baseDir);

            Path tmpFile = baseDir.resolve(snapshot.sessionId().value() + ".tmp");
            Path targetFile = baseDir.resolve(snapshot.sessionId().value() + ".json");

            mapper.writerWithDefaultPrettyPrinter().writeValue(tmpFile.toFile(), snapshot);
            Files.move(tmpFile, targetFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.debug("会话已保存: {}", targetFile);
        } catch (IOException e) {
            throw new SessionStoreException("Failed to save session: " + snapshot.sessionId(), e);
        }
    }

    @Override
    public Optional<SessionSnapshot> load(SessionId sessionId) {
        Path file = baseDir.resolve(sessionId.value() + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            SessionSnapshot snapshot = mapper.readValue(file.toFile(), SessionSnapshot.class);
            return Optional.of(snapshot);
        } catch (IOException e) {
            throw new SessionStoreException("Failed to load session: " + sessionId, e);
        }
    }

    @Override
    public boolean delete(SessionId sessionId) {
        try {
            Path file = baseDir.resolve(sessionId.value() + ".json");
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new SessionStoreException("Failed to delete session: " + sessionId, e);
        }
    }

    @Override
    public List<SessionSummary> listSessions() {
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }

        List<SessionSummary> summaries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.json")) {
            for (Path file : stream) {
                SessionSummary summary = parseSessionSummary(file);
                if (summary != null) {
                    summaries.add(summary);
                }
            }
        } catch (IOException e) {
            throw new SessionStoreException("Failed to list sessions", e);
        }

        summaries.sort(Comparator.comparing(SessionSummary::updatedAt).reversed());
        return summaries;
    }

    /**
     * 使用 Streaming API 仅读取 JSON 文件的头部元数据，避免完整反序列化。
     */
    private SessionSummary parseSessionSummary(Path file) {
        try (JsonParser parser = mapper.getFactory().createParser(file.toFile())) {
            String sessionIdValue = null;
            Instant createdAt = null;
            Instant updatedAt = null;
            int messageCount = 0;
            int compactionCount = 0;

            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case "sessionId" -> sessionIdValue = parseSessionIdFromToken(parser);
                    case "createdAt" -> createdAt = Instant.parse(parser.getText());
                    case "updatedAt" -> updatedAt = Instant.parse(parser.getText());
                    case "compactionCount" -> compactionCount = parser.getIntValue();
                    case "messages" -> {
                        // 仅统计数组长度，不反序列化内容
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            int depth = 1;
                            while (depth > 0) {
                                JsonToken token = parser.nextToken();
                                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                                    depth++;
                                    if (depth == 2) {
                                        messageCount++;
                                    }
                                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                                    depth--;
                                }
                            }
                        }
                    }
                    default -> parser.skipChildren();
                }
            }

            if (sessionIdValue == null) {
                return null;
            }

            return new SessionSummary(
                    SessionId.of(sessionIdValue),
                    createdAt != null ? createdAt : Instant.EPOCH,
                    updatedAt != null ? updatedAt : Instant.EPOCH,
                    messageCount, compactionCount);

        } catch (Exception e) {
            log.warn("解析会话摘要失败: {}", file.getFileName(), e);
            return null;
        }
    }

    /**
     * 从当前 token 解析 sessionId（支持字符串或对象格式）。
     */
    private static String parseSessionIdFromToken(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getText();
        }
        // SessionId record 序列化为 {"value": "xxx"}
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            String value = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if ("value".equals(parser.currentName())) {
                    parser.nextToken();
                    value = parser.getText();
                }
            }
            return value;
        }
        return null;
    }
}
