package com.sprinkleclaw.protocol.llm;

import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 聊天响应，包含内容块、停止原因、token 用量和模型标识。
 *
 * @param content    响应内容块列表
 * @param stopReason 停止原因
 * @param usage      token 用量统计
 * @param modelId    实际使用的模型标识
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public record ChatResponse(
        List<ContentBlock> content,
        StopReason stopReason,
        Usage usage,
        String modelId
) {
    /**
     * 提取响应中的所有工具调用请求。
     *
     * @return 工具调用块列表
     */
    public List<ToolUseBlock> toolCalls() {
        return content.stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .toList();
    }

    /**
     * 拼接响应中所有文本内容块的文本。
     *
     * @return 拼接后的完整文本
     */
    public String textContent() {
        return content.stream()
                .filter(ContentBlock.TextBlock.class::isInstance)
                .map(b -> ((ContentBlock.TextBlock) b).text())
                .collect(Collectors.joining());
    }

    /**
     * 创建空响应（无内容、零 token）。
     *
     * @param reason 停止原因
     * @return 空的 ChatResponse
     */
    public static ChatResponse empty(StopReason reason) {
        return new ChatResponse(List.of(), reason, new Usage(0, 0), "");
    }
}
