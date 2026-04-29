package icu.sprinkle.loom.gateway.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的 API Key 存储实现。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class InMemoryApiKeyStore implements ApiKeyStore {

    private final Map<String, ApiKeyEntry> keys = new ConcurrentHashMap<>();

    public InMemoryApiKeyStore() {
    }

    public InMemoryApiKeyStore(List<ApiKeyEntry> entries) {
        entries.forEach(e -> keys.put(e.key(), e));
    }

    /**
     * 注册一个 API Key。
     */
    public void register(ApiKeyEntry entry) {
        keys.put(entry.key(), entry);
    }

    /**
     * 移除一个 API Key。
     */
    public void remove(String apiKey) {
        keys.remove(apiKey);
    }

    @Override
    public Optional<ApiKeyEntry> lookup(String apiKey) {
        return Optional.ofNullable(keys.get(apiKey));
    }
}
