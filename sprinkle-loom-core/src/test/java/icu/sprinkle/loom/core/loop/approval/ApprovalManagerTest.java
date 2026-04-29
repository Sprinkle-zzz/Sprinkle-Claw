package icu.sprinkle.loom.core.loop.approval;

import icu.sprinkle.loom.tool.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalManagerTest {

    private static ApprovalRequest makeRequest(String id, Duration timeout) {
        return new ApprovalRequest(id, "bash", Map.of("cmd", "rm -rf /"),
                "Dangerous command", RiskLevel.HIGH, Instant.now(), timeout);
    }

    @Test
    void callbackApproval() {
        var manager = new ApprovalManager(req -> ApprovalResponse.approve());
        var request = makeRequest("req-1", Duration.ofSeconds(5));

        var response = manager.requestApproval(request);
        assertThat(response.approved()).isTrue();
        assertThat(manager.pendingCount()).isZero();
    }

    @Test
    void callbackDenial() {
        var manager = new ApprovalManager(req -> ApprovalResponse.deny("Too risky"));
        var request = makeRequest("req-2", Duration.ofSeconds(5));

        var response = manager.requestApproval(request);
        assertThat(response.approved()).isFalse();
        assertThat(response.reason()).contains("Too risky");
    }

    @Test
    void callbackWithModifiedInput() {
        var manager = new ApprovalManager(req ->
                ApprovalResponse.approveWithModifiedInput(Map.of("cmd", "echo safe")));
        var request = makeRequest("req-3", Duration.ofSeconds(5));

        var response = manager.requestApproval(request);
        assertThat(response.approved()).isTrue();
        assertThat(response.modifiedInput()).isPresent();
        assertThat(response.modifiedInput().get()).containsEntry("cmd", "echo safe");
    }

    @Test
    void timeoutProducesDenial() {
        // No callback, no resolve → timeout
        var manager = new ApprovalManager();
        var request = makeRequest("req-4", Duration.ofSeconds(1));

        var response = manager.requestApproval(request);
        assertThat(response.approved()).isFalse();
        assertThat(response.reason()).hasValueSatisfying(r -> assertThat(r).contains("timed out"));
    }

    @Test
    void resolveFromExternal() throws Exception {
        var manager = new ApprovalManager();
        var request = makeRequest("req-5", Duration.ofSeconds(5));

        // Resolve from another thread
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            manager.resolve("req-5", ApprovalResponse.approve());
        });

        var response = manager.requestApproval(request);
        assertThat(response.approved()).isTrue();
    }

    @Test
    void resolveNonExistentReturnsFalse() {
        var manager = new ApprovalManager();
        assertThat(manager.resolve("unknown", ApprovalResponse.approve())).isFalse();
    }

    @Test
    void callbackExceptionProducesDenial() {
        var manager = new ApprovalManager(req -> { throw new RuntimeException("Oops"); });
        var request = makeRequest("req-6", Duration.ofSeconds(5));

        var response = manager.requestApproval(request);
        assertThat(response.approved()).isFalse();
        assertThat(response.reason().orElse("")).contains("Callback error");
    }
}
