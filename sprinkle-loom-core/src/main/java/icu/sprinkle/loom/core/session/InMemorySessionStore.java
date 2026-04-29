package icu.sprinkle.loom.core.session;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的会话存储实现。
 *
 * <p>适用场景：</p>
 * <ul>
 *   <li>单元测试</li>
 *   <li>临时会话（不需要跨重启持久化）</li>
 *   <li>短生命周期应用</li>
 * </ul>
 *
 * <p>使用 {@link ConcurrentHashMap} 保证线程安全。</p>
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, SessionSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public void save(SessionSnapshot snapshot) {
        store.put(snapshot.sessionId().value(), snapshot);
    }

    @Override
    public Optional<SessionSnapshot> load(SessionId sessionId) {
        return Optional.ofNullable(store.get(sessionId.value()));
    }

    @Override
    public boolean delete(SessionId sessionId) {
        return store.remove(sessionId.value()) != null;
    }

    @Override
    public List<SessionSummary> listSessions() {
        return store.values().stream()
                .sorted(Comparator.comparing(SessionSnapshot::updatedAt).reversed())
                .map(s -> new SessionSummary(
                        s.sessionId(), s.createdAt(), s.updatedAt(),
                        s.messages().size(), s.compactionCount()))
                .toList();
    }
}
