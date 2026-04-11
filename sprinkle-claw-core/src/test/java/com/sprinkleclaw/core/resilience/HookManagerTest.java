package com.sprinkleclaw.core.resilience;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.loop.HookManager;
import com.sprinkleclaw.core.loop.LoopHook;
import com.sprinkleclaw.core.loop.ToolInterception;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HookManagerTest {

    @Test
    void hooksExecutedInPriorityOrder() {
        var executionOrder = new ArrayList<String>();

        LoopHook highPriority = new LoopHook() {
            @Override
            public int priority() {
                return 10;
            }

            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("high");
            }
        };

        LoopHook defaultPriority = new LoopHook() {
            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("default");
            }
        };

        LoopHook lowPriority = new LoopHook() {
            @Override
            public int priority() {
                return 200;
            }

            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("low");
            }
        };

        // 乱序注册
        var manager = new HookManager(List.of(lowPriority, defaultPriority, highPriority));
        manager.firePreLlmCall(mock(AgentContext.class), 1);

        assertThat(executionOrder).containsExactly("high", "default", "low");
    }

    @Test
    void skipShortCircuitsChain() {
        var executionOrder = new ArrayList<String>();

        LoopHook first = new LoopHook() {
            @Override
            public int priority() {
                return 10;
            }

            @Override
            public ToolInterception beforeToolExecution(AgentContext context, ToolUseBlock toolCall) {
                executionOrder.add("first");
                return new ToolInterception.Skip("blocked");
            }
        };

        LoopHook second = new LoopHook() {
            @Override
            public int priority() {
                return 20;
            }

            @Override
            public ToolInterception beforeToolExecution(AgentContext context, ToolUseBlock toolCall) {
                executionOrder.add("second");
                return ToolInterception.CONTINUE;
            }
        };

        var manager = new HookManager(List.of(first, second));
        var toolCall = new ToolUseBlock("id-1", "test_tool", Map.of());
        var result = manager.fireBeforeToolExecution(mock(AgentContext.class), toolCall);

        assertThat(result).isInstanceOf(ToolInterception.Skip.class);
        assertThat(executionOrder).containsExactly("first"); // second 不应被调用
    }

    @Test
    void hookExceptionDoesNotBreakChain() {
        var executionOrder = new ArrayList<String>();

        LoopHook failing = new LoopHook() {
            @Override
            public int priority() {
                return 10;
            }

            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("failing");
                throw new RuntimeException("Hook error");
            }
        };

        LoopHook healthy = new LoopHook() {
            @Override
            public int priority() {
                return 20;
            }

            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("healthy");
            }
        };

        var manager = new HookManager(List.of(failing, healthy));
        manager.firePreLlmCall(mock(AgentContext.class), 1);

        assertThat(executionOrder).containsExactly("failing", "healthy");
    }

    @Test
    void addHookMaintainsSortOrder() {
        var executionOrder = new ArrayList<String>();

        LoopHook existing = new LoopHook() {
            @Override
            public int priority() {
                return 100;
            }

            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("existing");
            }
        };

        LoopHook added = new LoopHook() {
            @Override
            public int priority() {
                return 50;
            }

            @Override
            public void preLlmCall(AgentContext context, int iteration) {
                executionOrder.add("added");
            }
        };

        var manager = new HookManager(List.of(existing));
        manager.addHook(added);
        manager.firePreLlmCall(mock(AgentContext.class), 1);

        assertThat(executionOrder).containsExactly("added", "existing");
    }
}
