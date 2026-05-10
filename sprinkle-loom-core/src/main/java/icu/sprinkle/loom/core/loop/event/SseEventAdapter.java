package icu.sprinkle.loom.core.loop.event;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 将 {@link AgentEvent} 转为 SSE（Server-Sent Events）格式字符串。
 * <p>为每个事件分配递增 {@code id}，支持 {@code Last-Event-ID} 断线续传。
 * 框架无关的工具类，可在任何 Web 框架中使用。</p>
 *
 * @author sprinkle
 * @since 0.5.0 (MVP4)
 */
public class SseEventAdapter {

    private final AtomicLong eventId = new AtomicLong(0);
    private final ObjectMapper mapper;
    private final ConcurrentSkipListMap<Long, AgentEvent> eventLog;
    private final int maxLogSize;

    public SseEventAdapter() {
        this(1000);
    }

    /**
     * @param maxLogSize 事件日志最大保留条数（用于断线续传），0 表示不保留
     */
    public SseEventAdapter(int maxLogSize) {
        this.mapper = new ObjectMapper();
        this.maxLogSize = maxLogSize;
        this.eventLog = maxLogSize > 0 ? new ConcurrentSkipListMap<>() : null;
    }

    /**
     * 将 AgentEvent 转为 SSE 格式字符串。
     */
    public String toSse(AgentEvent event) {
        long id = eventId.incrementAndGet();

        if (eventLog != null) {
            eventLog.put(id, event);
            trimLog();
        }

        String eventType = resolveEventType(event);
        String data = serializeData(event);

        return "id: " + id + "\n"
                + "event: " + eventType + "\n"
                + "data: " + data + "\n\n";
    }

    /**
     * 从指定 ID 之后重放事件（断线续传）。
     */
    public Stream<String> replayFrom(long lastEventId) {
        if (eventLog == null) return Stream.empty();
        return eventLog.tailMap(lastEventId, false).entrySet().stream()
                .map(e -> {
                    String type = resolveEventType(e.getValue());
                    String data = serializeData(e.getValue());
                    return "id: " + e.getKey() + "\nevent: " + type + "\ndata: " + data + "\n\n";
                });
    }

    private String resolveEventType(AgentEvent event) {
        return switch (event) {
            case AgentEvent.LlmToken t -> "llm_token";
            case AgentEvent.ThinkingToken t -> "thinking_token";
            case AgentEvent.ToolInputChunk t -> "tool_input_chunk";
            case AgentEvent.LlmCallStart s -> "llm_call_start";
            case AgentEvent.LlmCallEnd e -> "llm_call_end";
            case AgentEvent.ToolStart t -> "tool_start";
            case AgentEvent.ToolResult t -> "tool_result";
            case AgentEvent.ToolEnd t -> "tool_end";
            case AgentEvent.Compaction c -> "compaction";
            case AgentEvent.IterationComplete i -> "iteration_complete";
            case AgentEvent.AgentComplete c -> "agent_complete";
            case AgentEvent.AgentError e -> "agent_error";
            case AgentEvent.ErrorRecovered r -> "error_recovered";
            case AgentEvent.SessionResumed r -> "session_resumed";
            case AgentEvent.FallbackActivated f -> "fallback_activated";
            case AgentEvent.ApprovalRequired a -> "approval_required";
            case AgentEvent.ApprovalResolved a -> "approval_resolved";
            case AgentEvent.PreProcessComplete p -> "pre_process_complete";
        };
    }

    private String serializeData(AgentEvent event) {
        Map<String, Object> data = switch (event) {
            case AgentEvent.LlmToken t -> Map.of("token", t.token(), "index", t.index());
            case AgentEvent.ThinkingToken t -> Map.of("token", t.token(), "index", t.index());
            case AgentEvent.ToolInputChunk t -> Map.of("tool_use_id", t.toolUseId(),
                    "tool", t.toolName(), "chunk", t.inputChunk());
            case AgentEvent.LlmCallStart s -> Map.of("iteration", s.iteration());
            case AgentEvent.LlmCallEnd e -> mapOf("iteration", e.iteration(),
                    "input_tokens", e.inputTokens(), "output_tokens", e.outputTokens());
            case AgentEvent.ToolStart t -> Map.of("tool", t.toolName(), "tool_use_id", t.toolUseId());
            case AgentEvent.ToolResult t -> mapOf("tool", t.toolName(), "tool_use_id", t.toolUseId(),
                    "output", t.output(), "success", t.success(), "duration_ms", t.duration().toMillis(),
                    "truncated", t.truncated(), "original_bytes", t.originalBytes(),
                    "emitted_bytes", t.emittedBytes());
            case AgentEvent.ToolEnd t -> mapOf("tool", t.toolName(), "tool_use_id", t.toolUseId(),
                    "success", t.success(), "duration_ms", t.duration().toMillis());
            case AgentEvent.Compaction c -> mapOf("type", c.type().name(),
                    "before_tokens", c.beforeTokens(), "after_tokens", c.afterTokens());
            case AgentEvent.IterationComplete i -> Map.of("iteration", i.iteration(),
                    "stop_reason", i.stopReason().name());
            case AgentEvent.AgentComplete c -> mapOf("result", c.result().output(),
                    "iterations", c.result().totalIterations());
            case AgentEvent.AgentError e -> Map.of("error", e.error().getMessage(),
                    "phase", e.phase().name());
            case AgentEvent.ErrorRecovered r -> mapOf("error_type", r.errorType(),
                    "recovery", r.recovery(), "attempt", r.attempt());
            case AgentEvent.FallbackActivated f -> Map.of("primary", f.primaryModel(),
                    "fallback", f.fallbackModel(), "reason", f.reason());
            case AgentEvent.SessionResumed r -> Map.of("session_id", r.sessionId(),
                    "restored_messages", r.restoredMessages());
            case AgentEvent.ApprovalRequired a -> Map.of("request_id", a.requestId(),
                    "tool", a.toolName(), "description", a.description());
            case AgentEvent.ApprovalResolved a -> Map.of("request_id", a.requestId(),
                    "approved", a.approved());
            case AgentEvent.PreProcessComplete p -> Map.of("iteration", p.iteration(),
                    "tokens_saved", p.tokensSaved());
        };
        return toJson(data);
    }

    private void trimLog() {
        while (eventLog.size() > maxLogSize) {
            eventLog.pollFirstEntry();
        }
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 构建有序 Map（保持插入顺序）。
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... keyValues) {
        var map = new LinkedHashMap<K, V>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }
}
