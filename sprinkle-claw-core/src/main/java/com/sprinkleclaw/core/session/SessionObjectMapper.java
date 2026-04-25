package com.sprinkleclaw.core.session;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sprinkleclaw.protocol.message.CacheControl;
import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.Message;

/**
 * 会话序列化专用 {@link ObjectMapper} 工厂。
 *
 * <p>通过 Mixin 为 {@link Message} 和 {@link ContentBlock} 添加 Jackson 多态类型信息，
 * 避免在零依赖的 {@code sprinkle-claw-protocol} 模块中引入 Jackson 注解。</p>
 *
 * @author sprinkle
 * @since 2026/3/25
 */
final class SessionObjectMapper {

    private SessionObjectMapper() {
    }

    /**
     * 创建会话序列化用的 ObjectMapper。
     *
     * @return 配置好 Mixin 和 JavaTimeModule 的 ObjectMapper
     */
    static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.addMixIn(Message.class, MessageMixin.class);
        mapper.addMixIn(ContentBlock.class, ContentBlockMixin.class);
        mapper.addMixIn(CacheControl.class, CacheControlMixin.class);
        return mapper;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Message.UserMessage.class, name = "user"),
            @JsonSubTypes.Type(value = Message.AssistantMessage.class, name = "assistant"),
            @JsonSubTypes.Type(value = Message.ToolResultMessage.class, name = "tool_result")
    })
    abstract static class MessageMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
            @JsonSubTypes.Type(value = ContentBlock.ToolUseBlock.class, name = "tool_use"),
            @JsonSubTypes.Type(value = ContentBlock.ThinkingBlock.class, name = "thinking"),
            @JsonSubTypes.Type(value = ContentBlock.ImageBlock.class, name = "image"),
            @JsonSubTypes.Type(value = ContentBlock.DocumentBlock.class, name = "document"),
            @JsonSubTypes.Type(value = ContentBlock.AudioBlock.class, name = "audio")
    })
    abstract static class ContentBlockMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CacheControl.None.class, name = "none"),
            @JsonSubTypes.Type(value = CacheControl.Ephemeral.class, name = "ephemeral")
    })
    abstract static class CacheControlMixin {
    }
}
