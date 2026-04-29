package icu.sprinkle.loom.llm.ollama;

import icu.sprinkle.loom.llm.*;
import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Ollama 本地 LLM 的 {@link LlmProvider} 实现。
 * <p>使用 JDK HttpClient 调用 Ollama REST API（/api/chat），
 * 支持原生工具调用和 prompt-based 降级两种模式。</p>
 * <p>流式输出采用 NDJSON（非 SSE）格式。</p>
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public final class OllamaProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    private final OllamaConfig config;
    private final OllamaHttpClient http;
    private final OllamaProtocolMapper mapper;
    private final LlmCapabilities capabilities;

    public OllamaProvider(OllamaConfig config) {
        this.config = config;
        this.http = new OllamaHttpClient(config);
        this.mapper = new OllamaProtocolMapper();
        this.capabilities = OllamaCapabilityRegistry.resolve(config.model());
    }

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public LlmCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        try {
            String body = mapper.toOllamaRequest(request, config, capabilities, false);
            log.debug("Ollama chat request for model: {}", config.model());
            String raw = http.post("/api/chat", body);
            return mapper.toChatResponse(raw, capabilities);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorKind.UNKNOWN,
                    "Unexpected Ollama error: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatResponse streamChat(ChatRequest request, StreamCallback callback) throws LlmException {
        try {
            String body = mapper.toOllamaRequest(request, config, capabilities, true);
            log.debug("Ollama stream chat request for model: {}", config.model());
            InputStream stream = http.postStream("/api/chat", body);
            var parser = new OllamaStreamParser(callback, capabilities);
            return parser.consume(stream);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorKind.UNKNOWN,
                    "Unexpected Ollama stream error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> supportedModels() {
        return http.listModels();
    }

    @Override
    public boolean validateApiKey() {
        // Ollama 无 API Key，用 listModels 做连通性检查
        try {
            http.listModels();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
