package icu.sprinkle.loom.gateway.auth;

import java.util.Optional;

/**
 * API Key 存储接口。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface ApiKeyStore {

    /**
     * 根据 API Key 查找对应的认证信息。
     *
     * @param apiKey API Key 字符串
     * @return 存在则返回 ApiKeyEntry，不存在返回 empty
     */
    Optional<ApiKeyEntry> lookup(String apiKey);

    /**
     * API Key 条目。
     *
     * @param key      API Key
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param plan     计划层级名称
     */
    record ApiKeyEntry(String key, String tenantId, String userId, String plan) {
    }
}
