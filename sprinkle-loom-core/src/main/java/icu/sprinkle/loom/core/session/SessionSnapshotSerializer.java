package icu.sprinkle.loom.core.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.sprinkle.loom.protocol.message.Message;

import java.util.List;

/**
 * {@link SessionSnapshot} 序列化/反序列化工具。
 *
 * <p>处理 {@link Message} sealed interface 多态、{@link icu.sprinkle.loom.protocol.message.ContentBlock}
 * 多态（含多模态类型）、{@link java.time.Instant} 时间格式等。</p>
 *
 * <p>供 {@link SessionStore} 实现者使用，避免手动配置 Jackson 多态处理。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public final class SessionSnapshotSerializer {

    private static final ObjectMapper MAPPER = SessionObjectMapper.create();

    private SessionSnapshotSerializer() {
    }

    /**
     * 序列化 SessionSnapshot 为 JSON 字符串。
     */
    public static String serialize(SessionSnapshot snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new SessionStoreException("Failed to serialize SessionSnapshot", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化 SessionSnapshot。
     */
    public static SessionSnapshot deserialize(String json) {
        try {
            return MAPPER.readValue(json, SessionSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new SessionStoreException("Failed to deserialize SessionSnapshot", e);
        }
    }

    /**
     * 序列化消息列表为 JSON 字符串。
     */
    public static String serializeMessages(List<Message> messages) {
        try {
            var type = MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Message.class);
            return MAPPER.writerFor(type).writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            throw new SessionStoreException("Failed to serialize messages", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化消息列表。
     */
    public static List<Message> deserializeMessages(String json) {
        try {
            var type = MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Message.class);
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new SessionStoreException("Failed to deserialize messages", e);
        }
    }

    /**
     * 获取预配置的 ObjectMapper 副本（可用于自定义序列化场景）。
     */
    public static ObjectMapper objectMapper() {
        return MAPPER.copy();
    }
}
