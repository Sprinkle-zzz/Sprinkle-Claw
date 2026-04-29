package icu.sprinkle.loom.llm.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.sprinkle.loom.llm.LlmException;
import icu.sprinkle.loom.llm.LlmException.ErrorKind;
import icu.sprinkle.loom.llm.SharedHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * JDK HttpClient 包装，封装 Ollama REST API 的 HTTP 通信。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class OllamaHttpClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final OllamaConfig config;

    OllamaHttpClient(OllamaConfig config) {
        this.config = config;
        this.httpClient = SharedHttpClient.get();
    }

    /**
     * POST 请求并返回响应 JSON。
     */
    String post(String path, String jsonBody) throws LlmException {
        try {
            log.debug("Ollama POST {}{}", config.host(), path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.host() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(config.timeout())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            handleHttpError(response.statusCode(), response.body());
            return response.body();
        } catch (LlmException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR,
                    "Ollama not reachable at " + config.host()
                            + ". Is Ollama running? Start with: ollama serve", e);
        } catch (IOException e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR, "Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(ErrorKind.TIMEOUT, "Request interrupted", e);
        }
    }

    /**
     * POST 请求并返回原始 InputStream（用于 NDJSON 流式）。
     */
    InputStream postStream(String path, String jsonBody) throws LlmException {
        try {
            log.debug("Ollama stream POST {}{}", config.host(), path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.host() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(config.timeout())
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes());
                handleHttpError(response.statusCode(), errorBody);
            }
            return response.body();
        } catch (LlmException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR,
                    "Ollama not reachable at " + config.host()
                            + ". Is Ollama running? Start with: ollama serve", e);
        } catch (IOException e) {
            throw new LlmException(ErrorKind.NETWORK_ERROR, "Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(ErrorKind.TIMEOUT, "Request interrupted", e);
        }
    }

    /**
     * 列出本地已有模型。
     */
    List<String> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.host() + "/api/tags"))
                    .GET()
                    .timeout(config.timeout())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return List.of();
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                return List.of();
            }
            var result = new java.util.ArrayList<String>();
            for (JsonNode m : models) {
                result.add(m.path("name").asText());
            }
            return List.copyOf(result);
        } catch (Exception e) {
            log.debug("Failed to list Ollama models: {}", e.getMessage());
            return List.of();
        }
    }

    private void handleHttpError(int status, String body) throws LlmException {
        if (status >= 200 && status < 300) {
            return;
        }

        String message = extractErrorMessage(body);
        if (message.contains("not found")) {
            throw new LlmException(ErrorKind.INVALID_REQUEST,
                    "Model '" + config.model() + "' not found. Pull it first: ollama pull " + config.model());
        }
        ErrorKind kind = switch (status) {
            case 400 -> ErrorKind.INVALID_REQUEST;
            case 404 -> ErrorKind.INVALID_REQUEST;
            case 500, 502, 503 -> ErrorKind.SERVER_ERROR;
            default -> ErrorKind.UNKNOWN;
        };
        throw new LlmException(kind, "Ollama HTTP " + status + ": " + message);
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
