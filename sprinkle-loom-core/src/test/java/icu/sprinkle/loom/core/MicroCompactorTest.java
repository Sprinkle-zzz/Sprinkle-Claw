package icu.sprinkle.loom.core;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.context.CompactionResult;
import icu.sprinkle.loom.core.context.MicroCompactor;
import icu.sprinkle.loom.core.context.TokenEstimator;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import icu.sprinkle.loom.protocol.message.Message.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MicroCompactor 增强功能测试：去重、错误裁剪、受保护工具。
 *
 * @author sprinkle
 * @since 2026/4/4
 */
class MicroCompactorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    private AgentContext createContext() {
        return new AgentContext("test", AgentConfig.DEFAULT, List.of());
    }

    /**
     * 添加一轮工具调用（AssistantMessage + ToolResultMessage）。
     */
    private void addToolCall(AgentContext ctx, String toolCallId, String toolName,
                             Map<String, Object> input, String result, boolean isError) {
        ctx.addMessage(new AssistantMessage(
                List.of(new ContentBlock.ToolUseBlock(toolCallId, toolName, input)),
                StopReason.TOOL_USE));
        ctx.addMessage(isError
                ? ToolResultMessage.error(toolCallId, result)
                : ToolResultMessage.success(toolCallId, result));
    }

    @Test
    void deduplication_sameToolSameInput_keepsLatest() {
        MicroCompactor compactor = new MicroCompactor(10, estimator);
        AgentContext ctx = createContext();

        // 两次相同的 read_file 调用
        addToolCall(ctx, "call1", "read_file", Map.of("path", "/a.txt"),
                "content of a.txt first time " + "x".repeat(200), false);
        ctx.addMessage(UserMessage.of("continue"));
        addToolCall(ctx, "call2", "read_file", Map.of("path", "/a.txt"),
                "content of a.txt second time " + "x".repeat(200), false);

        CompactionResult result = compactor.compact(ctx);

        assertThat(result).isNotNull();
        // 第一次调用应被替换为去重占位符
        ToolResultMessage first = (ToolResultMessage) ctx.mutableMessages().get(1);
        assertThat(first.content()).contains("Duplicate");
        // 第二次调用应保留
        ToolResultMessage second = (ToolResultMessage) ctx.mutableMessages().get(4);
        assertThat(second.content()).contains("second time");
    }

    @Test
    void deduplication_sameToolDifferentInput_noDedup() {
        MicroCompactor compactor = new MicroCompactor(10, estimator);
        AgentContext ctx = createContext();

        addToolCall(ctx, "call1", "read_file", Map.of("path", "/a.txt"),
                "content of a.txt " + "x".repeat(200), false);
        addToolCall(ctx, "call2", "read_file", Map.of("path", "/b.txt"),
                "content of b.txt " + "x".repeat(200), false);

        CompactionResult result = compactor.compact(ctx);

        // 不同参数不去重
        ToolResultMessage first = (ToolResultMessage) ctx.mutableMessages().get(1);
        ToolResultMessage second = (ToolResultMessage) ctx.mutableMessages().get(3);
        assertThat(first.content()).contains("a.txt");
        assertThat(second.content()).contains("b.txt");
    }

    @Test
    void deduplication_protectedTool_noDedup() {
        MicroCompactor compactor = new MicroCompactor(10, estimator, Set.of("load_skill"));
        AgentContext ctx = createContext();

        addToolCall(ctx, "call1", "load_skill", Map.of("name", "test"),
                "skill content first " + "x".repeat(200), false);
        addToolCall(ctx, "call2", "load_skill", Map.of("name", "test"),
                "skill content second " + "x".repeat(200), false);

        compactor.compact(ctx);

        // 受保护工具不去重
        ToolResultMessage first = (ToolResultMessage) ctx.mutableMessages().get(1);
        assertThat(first.content()).contains("skill content first");
    }

    @Test
    void errorPruning_oldError_inputCleared() {
        MicroCompactor compactor = new MicroCompactor(10, estimator);
        AgentContext ctx = createContext();

        // 添加一个失败的工具调用
        addToolCall(ctx, "call1", "bash", Map.of("command", "rm -rf /"),
                "Permission denied: cannot remove /etc/passwd\nStack trace follows..." + "x".repeat(200),
                true);

        // 添加 4 轮用户消息（超过 ERROR_PRUNE_AFTER_ROUNDS=3）
        for (int i = 0; i < 4; i++) {
            ctx.addMessage(UserMessage.of("message " + i));
            addToolCall(ctx, "call_ok_" + i, "echo", Map.of("text", "hi"),
                    "hi", false);
        }

        CompactionResult result = compactor.compact(ctx);

        // 错误工具结果应被裁剪
        ToolResultMessage errorResult = (ToolResultMessage) ctx.mutableMessages().get(1);
        assertThat(errorResult.content()).contains("[Error: bash failed");
        assertThat(errorResult.content()).doesNotContain("Stack trace");
    }

    @Test
    void errorPruning_recentError_preserved() {
        MicroCompactor compactor = new MicroCompactor(10, estimator);
        AgentContext ctx = createContext();

        // 添加失败调用
        addToolCall(ctx, "call1", "bash", Map.of("command", "bad"),
                "Error: command not found " + "x".repeat(200), true);

        // 只添加 1 轮（不够 ERROR_PRUNE_AFTER_ROUNDS）
        ctx.addMessage(UserMessage.of("continue"));

        compactor.compact(ctx);

        // 近期错误不裁剪
        ToolResultMessage errorResult = (ToolResultMessage) ctx.mutableMessages().get(1);
        assertThat(errorResult.content()).contains("command not found");
    }

    @Test
    void protectedTools_notReplaced() {
        MicroCompactor compactor = new MicroCompactor(1, estimator, Set.of("load_skill"));
        AgentContext ctx = createContext();

        // 添加多个工具调用，其中 load_skill 应该被保护
        addToolCall(ctx, "call1", "load_skill", Map.of("name", "test"),
                "important skill content " + "x".repeat(200), false);
        addToolCall(ctx, "call2", "read_file", Map.of("path", "/a.txt"),
                "file content " + "x".repeat(200), false);
        addToolCall(ctx, "call3", "bash", Map.of("cmd", "ls"),
                "output " + "x".repeat(200), false);

        compactor.compact(ctx);

        // load_skill 的输出不应被替换
        ToolResultMessage skillResult = (ToolResultMessage) ctx.mutableMessages().get(1);
        assertThat(skillResult.content()).contains("important skill content");

        // read_file 可能被替换（因 keepRecent=1 只保留最新 1 个）
        ToolResultMessage readResult = (ToolResultMessage) ctx.mutableMessages().get(3);
        assertThat(readResult.content()).contains("Previous: used read_file");
    }
}
