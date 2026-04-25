package com.sprinkleclaw.core.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的 {@link MemoryStore} 实现（关键词匹配检索）。
 *
 * <p>仅适用于开发/测试——进程重启后所有记忆丢失。
 * 生产环境应实现持久化 MemoryStore（JDBC、向量数据库等）。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void record(MemoryEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        entries.put(entry.id(), entry);
    }

    @Override
    public List<MemoryEntry> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        String lowerQuery = query.toLowerCase();
        String[] queryTerms = lowerQuery.split("\\s+");

        return entries.values().stream()
                .map(entry -> new ScoredEntry(entry, score(entry.content().toLowerCase(), queryTerms)))
                .filter(se -> se.score > 0)
                .sorted(Comparator.comparingDouble(ScoredEntry::score).reversed())
                .limit(topK)
                .map(ScoredEntry::entry)
                .toList();
    }

    @Override
    public void delete(String memoryId) {
        entries.remove(memoryId);
    }

    @Override
    public List<MemoryEntry> listAll() {
        return entries.values().stream()
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .toList();
    }

    @Override
    public int size() {
        return entries.size();
    }

    private double score(String content, String[] queryTerms) {
        double total = 0;
        for (String term : queryTerms) {
            if (content.contains(term)) {
                total += 1.0;
            }
        }
        return total / queryTerms.length;
    }

    private record ScoredEntry(MemoryEntry entry, double score) {
    }
}
