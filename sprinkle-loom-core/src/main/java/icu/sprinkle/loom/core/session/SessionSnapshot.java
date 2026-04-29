package icu.sprinkle.loom.core.session;

import icu.sprinkle.loom.core.AgentConfig;
import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.protocol.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话快照，包含恢复一个会话所需的全部状态。
 *
 * <p>运行时 {@link AgentContext#sessionId()} 为 String，
 * 本 record 的 sessionId 为 {@link SessionId} 值对象。</p>
 *
 * @param sessionId       会话标识
 * @param messages        对话历史消息列表
 * @param metadata        自定义元数据（工具状态、TodoWrite 状态等）
 * @param systemPrompt    系统提示词
 * @param config          Agent 配置快照
 * @param createdAt       会话创建时间
 * @param updatedAt       最近更新时间
 * @param compactionCount 已执行的压缩次数
 *
 * @author sprinkle
 * @since 2026/3/25
 */
public record SessionSnapshot(
        SessionId sessionId,
        List<Message> messages,
        Map<String, Object> metadata,
        String systemPrompt,
        AgentConfig config,
        Instant createdAt,
        Instant updatedAt,
        int compactionCount
) {

    /**
     * 从 AgentContext 创建快照。
     *
     * @param context Agent 上下文（必须已设置 sessionId）
     * @return 快照实例
     * @throws IllegalStateException sessionId 为空时
     */
    public static SessionSnapshot from(AgentContext context) {
        String sid = context.sessionId();
        if (sid == null || sid.isBlank()) {
            throw new IllegalStateException("sessionId required for snapshot");
        }
        return new SessionSnapshot(
                SessionId.of(sid),
                context.messages(),
                Map.copyOf(context.allAttributes()),
                context.systemPrompt(),
                context.config(),
                context.createdAt(),
                Instant.now(),
                context.compactionCount()
        );
    }
}
