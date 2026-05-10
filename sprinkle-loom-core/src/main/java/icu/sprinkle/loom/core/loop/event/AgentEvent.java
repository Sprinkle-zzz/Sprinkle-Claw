package icu.sprinkle.loom.core.loop.event;

import icu.sprinkle.loom.core.AgentResult;
import icu.sprinkle.loom.core.context.CompactionResult;
import icu.sprinkle.loom.protocol.llm.StopReason;

import java.time.Duration;
import java.time.Instant;

/**
 * Agent 执行过程中的流式事件体系。
 * <p>sealed interface 保证所有事件类型在编译期可穷举，支持 pattern matching switch。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public sealed interface AgentEvent {

    /**
     * 事件时间戳。
     */
    Instant timestamp();

    // ===== LLM 调用事件 =====

    /**
     * LLM 文本 token 增量。
     */
    record LlmToken(Instant timestamp, String token, int index) implements AgentEvent {
    }

    /**
     * LLM 思考/推理 token 增量（Claude extended thinking）。
     */
    record ThinkingToken(Instant timestamp, String token, int index) implements AgentEvent {
    }

    /**
     * LLM 工具调用参数片段（流式 tool_use）。
     */
    record ToolInputChunk(Instant timestamp, String toolUseId, String toolName,
                          String inputChunk) implements AgentEvent {
    }

    /**
     * LLM 调用开始。
     */
    record LlmCallStart(Instant timestamp, int iteration) implements AgentEvent {
    }

    /**
     * LLM 调用结束。
     */
    record LlmCallEnd(Instant timestamp, int iteration,
                      int inputTokens, int outputTokens) implements AgentEvent {
    }

    // ===== 工具执行事件 =====

    /**
     * 工具开始执行。
     */
    record ToolStart(Instant timestamp, String toolName,
                     String toolUseId, Object input) implements AgentEvent {
    }

    /**
     * 工具执行结束。
     */
    record ToolEnd(Instant timestamp, String toolName, String toolUseId,
                   boolean success, Duration duration) implements AgentEvent {
    }

    /**
     * 工具执行结果。
     */
    record ToolResult(Instant timestamp, String toolName, String toolUseId,
                      String output, boolean success, Duration duration,
                      boolean truncated, int originalBytes, int emittedBytes) implements AgentEvent {
    }

    // ===== 压缩事件 =====

    /**
     * 上下文压缩完成。
     */
    record Compaction(Instant timestamp, CompactionResult.CompactionType type,
                      int beforeTokens, int afterTokens) implements AgentEvent {
    }

    // ===== 迭代与完成事件 =====

    /**
     * 单轮迭代完成。
     */
    record IterationComplete(Instant timestamp, int iteration,
                             StopReason stopReason) implements AgentEvent {
    }

    /**
     * Agent 执行完成。
     */
    record AgentComplete(Instant timestamp, AgentResult result) implements AgentEvent {
    }

    // ===== 错误与恢复事件 =====

    /**
     * Agent 执行出错。
     */
    record AgentError(Instant timestamp, Throwable error,
                      ErrorPhase phase) implements AgentEvent {
    }

    /**
     * 错误已恢复。
     */
    record ErrorRecovered(Instant timestamp, String errorType,
                          String recovery, int attempt) implements AgentEvent {
    }

    /**
     * Fallback 模型激活。
     */
    record FallbackActivated(Instant timestamp, String primaryModel,
                             String fallbackModel, String reason) implements AgentEvent {
    }

    // ===== 会话与审批事件 =====

    /**
     * 会话恢复。
     */
    record SessionResumed(Instant timestamp, String sessionId,
                          int restoredMessages) implements AgentEvent {
    }

    /**
     * HITL 审批请求。
     */
    record ApprovalRequired(Instant timestamp, String requestId,
                            String toolName, String description) implements AgentEvent {
    }

    /**
     * HITL 审批结果。
     */
    record ApprovalResolved(Instant timestamp, String requestId,
                            boolean approved) implements AgentEvent {
    }

    // ===== 预处理事件 =====

    /**
     * 预处理管线完成。
     */
    record PreProcessComplete(Instant timestamp, int iteration,
                              int tokensSaved) implements AgentEvent {
    }

    // ===== 枚举 =====

    /**
     * 错误发生的阶段。
     */
    enum ErrorPhase {
        LLM_CALL,
        TOOL_EXECUTION,
        COMPACTION,
        PRE_PROCESSING
    }

    // ===== 工厂方法 =====

    /**
     * 创建 LLM token 事件。
     */
    static LlmToken llmToken(String token, int index) {
        return new LlmToken(Instant.now(), token, index);
    }

    /**
     * 创建 Agent 完成事件。
     */
    static AgentComplete agentComplete(AgentResult result) {
        return new AgentComplete(Instant.now(), result);
    }

    /**
     * 创建 Agent 错误事件。
     */
    static AgentError agentError(Throwable error, ErrorPhase phase) {
        return new AgentError(Instant.now(), error, phase);
    }
}
