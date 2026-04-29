package icu.sprinkle.loom.core.loop.resilience;

import icu.sprinkle.loom.llm.LlmException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * 默认错误分类器实现。
 * <p>基于 HTTP 状态码、{@link LlmException.ErrorKind} 和异常类型推断 {@link ErrorType}。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class DefaultErrorClassifier implements ErrorClassifier {

    @Override
    public ErrorType classify(Throwable error, ErrorContext context) {
        // 优先从 LlmException.ErrorKind 映射
        if (error instanceof LlmException llmEx) {
            return classifyLlmException(llmEx, context);
        }

        // 非 LLM 异常
        return classifyNonLlm(error);
    }

    private ErrorType classifyLlmException(LlmException llmEx, ErrorContext context) {
        // 优先按 HTTP 状态码分类
        if (context.httpStatus() > 0) {
            return classifyByHttpStatus(context);
        }

        // 按 ErrorKind 分类
        return switch (llmEx.kind()) {
            case RATE_LIMIT -> ErrorType.RATE_LIMITED;
            case AUTH_ERROR -> ErrorType.AUTH_EXPIRED;
            case SERVER_ERROR -> ErrorType.SERVICE_OVERLOADED;
            case TIMEOUT -> ErrorType.NETWORK_ERROR;
            case NETWORK_ERROR -> ErrorType.NETWORK_ERROR;
            case INVALID_REQUEST -> classifyInvalidRequest(context);
            case CONTENT_FILTER -> ErrorType.INVALID_REQUEST;
            case UNKNOWN -> ErrorType.INVALID_REQUEST;
        };
    }

    private ErrorType classifyByHttpStatus(ErrorContext context) {
        return switch (context.httpStatus()) {
            case 429 -> ErrorType.RATE_LIMITED;
            case 529, 503 -> ErrorType.SERVICE_OVERLOADED;
            case 401, 403 -> ErrorType.AUTH_EXPIRED;
            case 400 -> classifyInvalidRequest(context);
            default -> {
                if (context.httpStatus() >= 500) {
                    yield ErrorType.SERVICE_OVERLOADED;
                }
                yield ErrorType.INVALID_REQUEST;
            }
        };
    }

    private ErrorType classifyInvalidRequest(ErrorContext context) {
        String code = context.errorCode();
        if (code != null) {
            return switch (code) {
                case "context_length_exceeded",
                     "max_tokens_exceeded",
                     "string_above_max_length" -> ErrorType.PROMPT_TOO_LONG;
                default -> ErrorType.INVALID_REQUEST;
            };
        }

        // 消息模式匹配
        String msg = context.errorMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if ((lower.contains("context") && lower.contains("length"))
                    || lower.contains("too many tokens")
                    || lower.contains("maximum context")) {
                return ErrorType.PROMPT_TOO_LONG;
            }
        }

        return ErrorType.INVALID_REQUEST;
    }

    private ErrorType classifyNonLlm(Throwable error) {
        if (error instanceof SocketTimeoutException
                || error instanceof ConnectException) {
            return ErrorType.NETWORK_ERROR;
        }
        if (error instanceof java.io.IOException) {
            return ErrorType.NETWORK_ERROR;
        }
        return ErrorType.INVALID_REQUEST;
    }
}
