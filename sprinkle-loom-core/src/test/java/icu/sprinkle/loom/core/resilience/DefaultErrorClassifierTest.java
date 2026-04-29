package icu.sprinkle.loom.core.resilience;

import icu.sprinkle.loom.core.loop.resilience.DefaultErrorClassifier;
import icu.sprinkle.loom.core.loop.resilience.ErrorClassifier;
import icu.sprinkle.loom.core.loop.resilience.ErrorClassifier.ErrorContext;
import icu.sprinkle.loom.core.loop.resilience.ErrorType;
import icu.sprinkle.loom.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultErrorClassifierTest {

    private ErrorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new DefaultErrorClassifier();
    }

    @Test
    void classifyRateLimitByErrorKind() {
        var error = new LlmException(LlmException.ErrorKind.RATE_LIMIT, "Too many requests");
        var ctx = ErrorContext.ofNonHttp("Too many requests", 0);
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.RATE_LIMITED);
    }

    @Test
    void classifyRateLimitByHttpStatus429() {
        var error = new LlmException(LlmException.ErrorKind.RATE_LIMIT, "429");
        var ctx = ErrorContext.ofLlm(429, null, "Rate limited", 0, false, "anthropic", "claude");
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.RATE_LIMITED);
    }

    @Test
    void classifyServiceOverloaded529() {
        var error = new LlmException(LlmException.ErrorKind.SERVER_ERROR, "overloaded");
        var ctx = ErrorContext.ofLlm(529, null, "Service overloaded", 0, false, "anthropic", "claude");
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.SERVICE_OVERLOADED);
    }

    @Test
    void classifyServiceOverloaded503() {
        var error = new LlmException(LlmException.ErrorKind.SERVER_ERROR, "unavailable");
        var ctx = ErrorContext.ofLlm(503, null, "Service unavailable", 0, false, "openai", "gpt-4");
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.SERVICE_OVERLOADED);
    }

    @Test
    void classifyAuthExpired() {
        var error = new LlmException(LlmException.ErrorKind.AUTH_ERROR, "Invalid API key");
        var ctx = ErrorContext.ofLlm(401, null, "Invalid API key", 0, false, "anthropic", "claude");
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.AUTH_EXPIRED);
    }

    @Test
    void classifyPromptTooLongByErrorCode() {
        var error = new LlmException(LlmException.ErrorKind.INVALID_REQUEST, "too long");
        var ctx = ErrorContext.ofLlm(400, "context_length_exceeded", "Too long", 0, false, "openai", "gpt-4");
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void classifyPromptTooLongByMessage() {
        var error = new LlmException(LlmException.ErrorKind.INVALID_REQUEST, "context length exceeded");
        var ctx = ErrorContext.ofLlm(400, null, "The context length has exceeded the limit", 0, false, null, null);
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void classifyNetworkErrorSocketTimeout() {
        var error = new SocketTimeoutException("Read timed out");
        var ctx = ErrorContext.ofNonHttp("Read timed out", 0);
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.NETWORK_ERROR);
    }

    @Test
    void classifyNetworkErrorConnectException() {
        var error = new ConnectException("Connection refused");
        var ctx = ErrorContext.ofNonHttp("Connection refused", 0);
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.NETWORK_ERROR);
    }

    @Test
    void classifyServerErrorByKind() {
        var error = new LlmException(LlmException.ErrorKind.SERVER_ERROR, "Internal error");
        var ctx = ErrorContext.ofNonHttp("Internal error", 0);
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.SERVICE_OVERLOADED);
    }

    @Test
    void classifyUnknownAsInvalidRequest() {
        var error = new RuntimeException("Something unexpected");
        var ctx = ErrorContext.ofNonHttp("Something unexpected", 0);
        assertThat(classifier.classify(error, ctx)).isEqualTo(ErrorType.INVALID_REQUEST);
    }
}
