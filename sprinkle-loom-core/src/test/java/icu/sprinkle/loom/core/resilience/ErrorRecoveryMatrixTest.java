package icu.sprinkle.loom.core.resilience;

import icu.sprinkle.loom.core.loop.resilience.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorRecoveryMatrixTest {

    private ErrorRecoveryMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = new ErrorRecoveryMatrix(ResilienceConfig.defaults());
    }

    @Test
    void rateLimitedFirstAttemptRetries() {
        var ctx = RecoveryContext.of(ErrorType.RATE_LIMITED, 0, "429");
        var recovery = matrix.resolve(ErrorType.RATE_LIMITED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Retry.class);
    }

    @Test
    void rateLimitedExhaustedWithFallback() {
        var ctx = RecoveryContext.of(ErrorType.RATE_LIMITED, 5, "429")
                .withFallbackModel(true);
        var recovery = matrix.resolve(ErrorType.RATE_LIMITED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Fallback.class);
    }

    @Test
    void rateLimitedExhaustedWithoutFallbackAborts() {
        var ctx = RecoveryContext.of(ErrorType.RATE_LIMITED, 5, "429")
                .withFallbackModel(false);
        var recovery = matrix.resolve(ErrorType.RATE_LIMITED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Abort.class);
    }

    @Test
    void serviceOverloadedBackgroundTaskAborts() {
        var ctx = RecoveryContext.of(ErrorType.SERVICE_OVERLOADED, 0, "529")
                .withBackgroundTask(true);
        var recovery = matrix.resolve(ErrorType.SERVICE_OVERLOADED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Abort.class);
    }

    @Test
    void serviceOverloadedForegroundRetries() {
        var ctx = RecoveryContext.of(ErrorType.SERVICE_OVERLOADED, 0, "529");
        var recovery = matrix.resolve(ErrorType.SERVICE_OVERLOADED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Retry.class);
    }

    @Test
    void authExpiredAlwaysAborts() {
        var ctx = RecoveryContext.of(ErrorType.AUTH_EXPIRED, 0, "401");
        var recovery = matrix.resolve(ErrorType.AUTH_EXPIRED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Abort.class);
    }

    @Test
    void promptTooLongCompactsFirst() {
        var ctx = RecoveryContext.of(ErrorType.PROMPT_TOO_LONG, 0, "context_length_exceeded")
                .withCompacted(false);
        var recovery = matrix.resolve(ErrorType.PROMPT_TOO_LONG, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.CompactAndRetry.class);
    }

    @Test
    void promptTooLongAfterCompactionTruncates() {
        var ctx = RecoveryContext.of(ErrorType.PROMPT_TOO_LONG, 0, "context_length_exceeded")
                .withCompacted(true);
        var recovery = matrix.resolve(ErrorType.PROMPT_TOO_LONG, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.TruncateAndRetry.class);
    }

    @Test
    void promptTooLongAfterAllRetriesAborts() {
        var ctx = RecoveryContext.of(ErrorType.PROMPT_TOO_LONG, 2, "context_length_exceeded")
                .withCompacted(true);
        var recovery = matrix.resolve(ErrorType.PROMPT_TOO_LONG, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Abort.class);
    }

    @Test
    void outputTruncatedEscalates() {
        var ctx = RecoveryContext.of(ErrorType.OUTPUT_TRUNCATED, 0, "max_tokens");
        var recovery = matrix.resolve(ErrorType.OUTPUT_TRUNCATED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.EscalateOutput.class);
    }

    @Test
    void networkErrorRetries() {
        var ctx = RecoveryContext.of(ErrorType.NETWORK_ERROR, 0, "timeout");
        var recovery = matrix.resolve(ErrorType.NETWORK_ERROR, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Retry.class);
    }

    @Test
    void networkErrorExhaustedWithFallback() {
        var ctx = RecoveryContext.of(ErrorType.NETWORK_ERROR, 3, "timeout")
                .withFallbackModel(true);
        var recovery = matrix.resolve(ErrorType.NETWORK_ERROR, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Fallback.class);
    }

    @Test
    void toolTimeoutIgnores() {
        var ctx = RecoveryContext.of(ErrorType.TOOL_TIMEOUT, 0, "timeout");
        var recovery = matrix.resolve(ErrorType.TOOL_TIMEOUT, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Ignore.class);
    }

    @Test
    void toolSuspendedSuspends() {
        var ctx = RecoveryContext.of(ErrorType.TOOL_SUSPENDED, 0, "approval");
        var recovery = matrix.resolve(ErrorType.TOOL_SUSPENDED, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Suspend.class);
    }

    @Test
    void doomLoopAborts() {
        var ctx = RecoveryContext.of(ErrorType.DOOM_LOOP, 0, "doom loop");
        var recovery = matrix.resolve(ErrorType.DOOM_LOOP, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Abort.class);
    }

    @Test
    void maxIterationsAborts() {
        var ctx = RecoveryContext.of(ErrorType.MAX_ITERATIONS, 0, "max iterations");
        var recovery = matrix.resolve(ErrorType.MAX_ITERATIONS, ctx);
        assertThat(recovery).isInstanceOf(ErrorRecovery.Abort.class);
    }
}
