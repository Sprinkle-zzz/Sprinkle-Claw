package icu.sprinkle.loom.gateway.auth;

import java.util.Optional;
import java.util.Set;

/**
 * API Key 认证提供者。
 * <p>支持两种格式：
 * <ul>
 *   <li>{@code Bearer sk-xxx} — 从 Authorization header 提取</li>
 *   <li>{@code sk-xxx} — 直接传入 API Key</li>
 * </ul>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class ApiKeyAuthProvider implements AuthProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyStore store;

    public ApiKeyAuthProvider(ApiKeyStore store) {
        this.store = store;
    }

    @Override
    public Optional<AuthContext> authenticate(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return Optional.empty();
        }

        String apiKey = authHeader.startsWith(BEARER_PREFIX)
                ? authHeader.substring(BEARER_PREFIX.length()).trim()
                : authHeader.trim();

        return store.lookup(apiKey).map(entry -> new AuthContext(
                entry.userId(),
                entry.tenantId(),
                entry.plan(),
                Set.of(),
                "api-key"
        ));
    }
}
