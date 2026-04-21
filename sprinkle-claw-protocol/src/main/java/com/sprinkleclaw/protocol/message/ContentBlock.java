package com.sprinkleclaw.protocol.message;

import java.util.Map;

/**
 * LLM 响应中的内容块。
 * 统一封装 Anthropic（text/tool_use/thinking）和 OpenAI（text/tool_calls）的内容格式，
 * 同时支持多模态内容（image/document/audio）。
 *
 * @author sprinkle
 * @since 2026/3/17
 */
public sealed interface ContentBlock {

    String MIME_PNG = "image/png";
    String MIME_JPEG = "image/jpeg";
    String MIME_GIF = "image/gif";
    String MIME_WEBP = "image/webp";
    String MIME_PDF = "application/pdf";
    String MIME_WAV = "audio/wav";
    String MIME_MP3 = "audio/mpeg";

    /**
     * 纯文本内容块。
     *
     * @param text         文本内容
     * @param cacheControl 缓存控制标记
     */
    record TextBlock(String text, CacheControl cacheControl) implements ContentBlock {
        public TextBlock {
            if (cacheControl == null) cacheControl = CacheControl.NONE;
        }

        public TextBlock(String text) {
            this(text, CacheControl.NONE);
        }
    }

    /**
     * 工具调用请求块，表示 LLM 希望调用某个工具。
     *
     * @param id           工具调用的唯一标识，用于关联调用结果
     * @param name         要调用的工具名称
     * @param input        工具输入参数（键值对）
     * @param cacheControl 缓存控制标记
     */
    record ToolUseBlock(String id, String name, Map<String, Object> input,
                        CacheControl cacheControl) implements ContentBlock {
        public ToolUseBlock {
            if (cacheControl == null) cacheControl = CacheControl.NONE;
        }

        public ToolUseBlock(String id, String name, Map<String, Object> input) {
            this(id, name, input, CacheControl.NONE);
        }
    }

    /**
     * LLM 内部推理内容块。
     * <p>Anthropic 以 {@code thinking} content block 返回；OpenAI 推理模型通过 {@code reasoning_tokens} 计量，
     * 推理内容不直接暴露给用户，但可通过 {@link com.sprinkleclaw.protocol.llm.Usage#reasoningTokens()} 获取推理 token 消耗。</p>
     * <p>ThinkingBlock 不参与缓存，因此不提供 CacheControl 字段。</p>
     *
     * @param thinking 推理/思考文本
     */
    record ThinkingBlock(String thinking) implements ContentBlock {
    }

    /**
     * 图片内容块。{@code base64Data} 和 {@code url} 必须且仅提供一个。
     *
     * @param mediaType    MIME 类型（如 image/png）
     * @param base64Data   Base64 编码的图片数据
     * @param url          图片 URL
     * @param cacheControl 缓存控制标记
     * @since 0.8.0 (MVP7)
     */
    record ImageBlock(String mediaType, String base64Data, String url,
                      CacheControl cacheControl) implements ContentBlock {
        public ImageBlock {
            if (cacheControl == null) cacheControl = CacheControl.NONE;
            boolean hasData = base64Data != null && !base64Data.isEmpty();
            boolean hasUrl = url != null && !url.isEmpty();
            if (hasData == hasUrl) {
                throw new IllegalArgumentException(
                        "ImageBlock requires exactly one of base64Data or url");
            }
        }

        public static ImageBlock ofBase64(String mediaType, String data) {
            return new ImageBlock(mediaType, data, null, CacheControl.NONE);
        }

        public static ImageBlock ofUrl(String url) {
            return new ImageBlock(null, null, url, CacheControl.NONE);
        }
    }

    /**
     * 文档内容块（PDF 等）。{@code base64Data} 和 {@code url} 必须且仅提供一个。
     *
     * @param mediaType    MIME 类型（如 application/pdf）
     * @param base64Data   Base64 编码的文档数据
     * @param name         文档名称
     * @param url          文档 URL
     * @param cacheControl 缓存控制标记
     * @since 0.8.0 (MVP7)
     */
    record DocumentBlock(String mediaType, String base64Data, String name, String url,
                         CacheControl cacheControl) implements ContentBlock {
        public DocumentBlock {
            if (cacheControl == null) cacheControl = CacheControl.NONE;
            boolean hasData = base64Data != null && !base64Data.isEmpty();
            boolean hasUrl = url != null && !url.isEmpty();
            if (hasData == hasUrl) {
                throw new IllegalArgumentException(
                        "DocumentBlock requires exactly one of base64Data or url");
            }
        }

        public static DocumentBlock ofBase64(String mediaType, String data, String name) {
            return new DocumentBlock(mediaType, data, name, null, CacheControl.NONE);
        }

        public static DocumentBlock ofUrl(String url, String name) {
            return new DocumentBlock(null, null, name, url, CacheControl.NONE);
        }
    }

    /**
     * 音频内容块。{@code base64Data} 和 {@code url} 必须且仅提供一个。
     *
     * @param mediaType    MIME 类型（如 audio/wav）
     * @param base64Data   Base64 编码的音频数据
     * @param url          音频 URL
     * @param cacheControl 缓存控制标记
     * @since 0.8.0 (MVP7)
     */
    record AudioBlock(String mediaType, String base64Data, String url,
                      CacheControl cacheControl) implements ContentBlock {
        public AudioBlock {
            if (cacheControl == null) cacheControl = CacheControl.NONE;
            boolean hasData = base64Data != null && !base64Data.isEmpty();
            boolean hasUrl = url != null && !url.isEmpty();
            if (hasData == hasUrl) {
                throw new IllegalArgumentException(
                        "AudioBlock requires exactly one of base64Data or url");
            }
        }

        public static AudioBlock ofBase64(String mediaType, String data) {
            return new AudioBlock(mediaType, data, null, CacheControl.NONE);
        }

        public static AudioBlock ofUrl(String url) {
            return new AudioBlock(null, null, url, CacheControl.NONE);
        }
    }
}
