package icu.sprinkle.loom.gateway.auth;

import java.util.Optional;

/**
 * 认证提供者 SPI。支持多种认证方式（API Key、JWT 等）。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface AuthProvider {

    /**
     * 尝试认证。
     *
     * @param authHeader 认证凭据（API Key、Bearer token 等）
     * @return 认证成功返回 AuthContext，无法处理或失败返回 empty
     */
    Optional<AuthContext> authenticate(String authHeader);
}
