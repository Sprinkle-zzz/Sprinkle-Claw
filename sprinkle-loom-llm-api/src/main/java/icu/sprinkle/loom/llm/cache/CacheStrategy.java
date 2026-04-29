package icu.sprinkle.loom.llm.cache;

/**
 * 预定义的 Prompt 缓存策略枚举。
 *
 * @author sprinkle
 * @since 0.8.0 (MVP7)
 */
public enum CacheStrategy {

    /** 不自动打标，由用户手动设置 CacheControl。 */
    MANUAL,

    /** 仅给 system prompt 打 Ephemeral。 */
    AUTO_SYSTEM_PROMPT,

    /** 给 system prompt + 工具定义打 Ephemeral。 */
    AUTO_SYSTEM_AND_TOOLS,

    /** 激进模式：system + tools + 对话前缀中 ≥1024 token 的稳定部分。 */
    AUTO_AGGRESSIVE
}
