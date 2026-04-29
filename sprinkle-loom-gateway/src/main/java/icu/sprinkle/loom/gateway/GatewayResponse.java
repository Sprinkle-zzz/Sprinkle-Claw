package icu.sprinkle.loom.gateway;

import icu.sprinkle.loom.protocol.llm.Usage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管控响应封装。包含 Agent 执行输出 + 过滤器附加的响应头。
 *
 * @param output       Agent 最终文本输出
 * @param usage        Token 用量
 * @param elapsedMs    执行耗时（毫秒）
 * @param headers      过滤器附加的响应头（如 X-RateLimit-*）
 * @param attributes   过滤器间共享的可变上下文
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public record GatewayResponse(
        String output,
        Usage usage,
        long elapsedMs,
        Map<String, String> headers,
        Map<String, Object> attributes
) {

    /**
     * 从 Agent 执行结果构建。
     */
    public static GatewayResponse of(String output, Usage usage, long elapsedMs) {
        return new GatewayResponse(output, usage, elapsedMs,
                new LinkedHashMap<>(), new LinkedHashMap<>());
    }
}
