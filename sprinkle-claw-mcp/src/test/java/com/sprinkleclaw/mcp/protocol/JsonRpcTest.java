package com.sprinkleclaw.mcp.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void request_create_setsVersion() {
        var req = JsonRpc.Request.create(1, "test/method", null);
        assertThat(req.jsonrpc()).isEqualTo("2.0");
        assertThat(req.id()).isEqualTo(1);
        assertThat(req.method()).isEqualTo("test/method");
    }

    @Test
    void request_notification_hasNullId() {
        var req = JsonRpc.Request.notification("notifications/test", null);
        assertThat(req.id()).isNull();
    }

    @Test
    void request_serialization_roundTrip() throws Exception {
        var params = MAPPER.createObjectNode();
        params.put("key", "value");
        var req = JsonRpc.Request.create(42, "tools/list", params);

        var json = MAPPER.writeValueAsString(req);
        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"id\":42");
        assertThat(json).contains("\"method\":\"tools/list\"");

        var parsed = MAPPER.readValue(json, JsonRpc.Request.class);
        assertThat(parsed.method()).isEqualTo("tools/list");
    }

    @Test
    void response_success_hasNoError() {
        var result = MAPPER.createObjectNode();
        result.put("status", "ok");
        var resp = JsonRpc.Response.success(1, result);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.error()).isNull();
        assertThat(resp.result().get("status").asText()).isEqualTo("ok");
    }

    @Test
    void response_error_hasNoResult() {
        var error = JsonRpc.Error.of(-32601, "Method not found");
        var resp = JsonRpc.Response.error(1, error);

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.result()).isNull();
        assertThat(resp.error().code()).isEqualTo(-32601);
    }

    @Test
    void response_serialization_roundTrip() throws Exception {
        var result = MAPPER.createObjectNode();
        result.put("data", "test");
        var resp = JsonRpc.Response.success(1, result);

        var json = MAPPER.writeValueAsString(resp);
        var parsed = MAPPER.readValue(json, JsonRpc.Response.class);
        assertThat(parsed.isSuccess()).isTrue();
        assertThat(parsed.id()).isEqualTo(1);
    }

    @Test
    void notification_serialization_omitsNullId() throws Exception {
        var req = JsonRpc.Request.notification("notifications/initialized", null);
        var json = MAPPER.writeValueAsString(req);
        assertThat(json).doesNotContain("\"id\"");
    }

    @Test
    void error_of_createsWithNullData() {
        var error = JsonRpc.Error.of(-32700, "Parse error");
        assertThat(error.data()).isNull();
        assertThat(error.message()).isEqualTo("Parse error");
    }
}
