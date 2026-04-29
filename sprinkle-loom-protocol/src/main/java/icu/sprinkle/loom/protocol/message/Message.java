package icu.sprinkle.loom.protocol.message;

import icu.sprinkle.loom.protocol.llm.StopReason;

import java.util.List;

/**
 * 对话消息，统一封装 Anthropic（user/assistant）和 OpenAI（system/user/assistant/tool）的角色格式。
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public sealed interface Message {

    /**
     * 用户消息。
     *
     * @param content 消息内容块列表
     */
    record UserMessage(List<ContentBlock> content) implements Message {
        /**
         * 快捷创建纯文本用户消息。
         *
         * @param text 文本内容
         * @return 包含单个 TextBlock 的用户消息
         */
        public static UserMessage of(String text) {
            return new UserMessage(List.of(new ContentBlock.TextBlock(text)));
        }
    }

    /**
     * 助手（LLM）回复消息。
     *
     * @param content    回复内容块列表（可包含文本、工具调用等）
     * @param stopReason 停止原因
     */
    record AssistantMessage(List<ContentBlock> content, StopReason stopReason) implements Message {
    }

    /**
     * 工具执行结果消息，用于将工具执行结果反馈给 LLM。
     *
     * @param toolCallId 关联的工具调用 ID
     * @param content    工具执行输出内容
     * @param isError    是否为错误结果
     */
    record ToolResultMessage(String toolCallId, String content, boolean isError) implements Message {
        /**
         * 创建成功的工具结果消息。
         */
        public static ToolResultMessage success(String toolCallId, String content) {
            return new ToolResultMessage(toolCallId, content, false);
        }

        /**
         * 创建失败的工具结果消息。
         */
        public static ToolResultMessage error(String toolCallId, String content) {
            return new ToolResultMessage(toolCallId, content, true);
        }
    }
}
