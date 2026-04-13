package com.sprinkleclaw.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 消息定义。
 *
 * @author sprinkle
 * @since 2026/4/13
 */
public final class JsonRpc {

    public static final String VERSION = "2.0";

    private JsonRpc() {}

    /**
     * JSON-RPC 请求。
     *
     * @param jsonrpc 协议版本
     * @param id      请求 ID（通知时为 null）
     * @param method  方法名
     * @param params  参数
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            String jsonrpc,
            Object id,
            String method,
            JsonNode params
    ) {
        public static Request create(Object id, String method, JsonNode params) {
            return new Request(VERSION, id, method, params);
        }

        public static Request notification(String method, JsonNode params) {
            return new Request(VERSION, null, method, params);
        }
    }

    /**
     * JSON-RPC 响应。
     *
     * @param jsonrpc 协议版本
     * @param id      关联的请求 ID
     * @param result  成功时的结果
     * @param error   失败时的错误
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            String jsonrpc,
            Object id,
            JsonNode result,
            Error error
    ) {
        public static Response success(Object id, JsonNode result) {
            return new Response(VERSION, id, result, null);
        }

        public static Response error(Object id, Error error) {
            return new Response(VERSION, id, null, error);
        }

        @JsonIgnore
        public boolean isSuccess() {
            return error == null;
        }
    }

    /**
     * JSON-RPC 错误对象。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param data    附加数据
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int code, String message, JsonNode data) {

        public static Error of(int code, String message) {
            return new Error(code, message, null);
        }
    }
}
