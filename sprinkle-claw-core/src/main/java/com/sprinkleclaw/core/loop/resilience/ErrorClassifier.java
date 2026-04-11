package com.sprinkleclaw.core.loop.resilience;

/**
 * 错误分类器 SPI 接口。
 * <p>从异常和上下文信息中提取 {@link ErrorType}，供 {@link ErrorRecoveryMatrix} 查询恢复策略。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public interface ErrorClassifier {

    /**
     * 从异常中提取错误类型。
     *
     * @param error   原始异常
     * @param context 错误发生时的上下文
     * @return 错误类型
     */
    ErrorType classify(Throwable error, ErrorContext context);

    /**
     * 错误发生时的上下文信息。
     *
     * @param httpStatus   HTTP 响应码（LLM API 错误时有值，其他为 0）
     * @param errorCode    API 错误码（如 "context_length_exceeded"）
     * @param errorMessage 原始错误消息
     * @param attempt      当前重试次数
     * @param isStreaming  是否在流式模式
     * @param provider     LLM Provider 名称
     * @param model        模型名称
     */
    record ErrorContext(
            int httpStatus,
            String errorCode,
            String errorMessage,
            int attempt,
            boolean isStreaming,
            String provider,
            String model
    ) {
        /**
         * 创建 LLM API 错误上下文。
         */
        public static ErrorContext ofLlm(int httpStatus, String errorCode, String errorMessage,
                                         int attempt, boolean isStreaming,
                                         String provider, String model) {
            return new ErrorContext(httpStatus, errorCode, errorMessage,
                    attempt, isStreaming, provider, model);
        }

        /**
         * 创建非 HTTP 错误上下文。
         */
        public static ErrorContext ofNonHttp(String errorMessage, int attempt) {
            return new ErrorContext(0, null, errorMessage, attempt, false, null, null);
        }
    }
}
