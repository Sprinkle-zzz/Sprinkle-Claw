package com.sprinkleclaw.core.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 长期记忆条目。
 *
 * @param id        唯一标识
 * @param content   记忆内容（自然语言）
 * @param metadata  附加元数据（来源、标签等）
 * @param createdAt 创建时间
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public record MemoryEntry(
        String id,
        String content,
        Map<String, String> metadata,
        Instant createdAt
) {
    public MemoryEntry(String content) {
        this(UUID.randomUUID().toString(), content, Map.of(), Instant.now());
    }

    public MemoryEntry(String content, Map<String, String> metadata) {
        this(UUID.randomUUID().toString(), content, metadata, Instant.now());
    }
}
