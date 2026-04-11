package com.sprinkleclaw.core.loop;

import com.sprinkleclaw.core.context.AgentContext;
import com.sprinkleclaw.core.context.CompactionResult;
import com.sprinkleclaw.protocol.llm.ChatResponse;
import com.sprinkleclaw.protocol.message.ContentBlock.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hook 优先级排序管理器。
 * <p>按 {@link LoopHook#priority()} 排序执行所有 Hook，低值高优先。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private volatile List<LoopHook> sortedHooks;

    public HookManager(List<LoopHook> hooks) {
        this.sortedHooks = sortByPriority(hooks != null ? hooks : List.of());
    }

    /**
     * 按优先级触发 preLlmCall。
     */
    public void firePreLlmCall(AgentContext context, int iteration) {
        for (var hook : sortedHooks) {
            try {
                hook.preLlmCall(context, iteration);
            } catch (Exception e) {
                log.warn("Hook preLlmCall 异常 ({}): {}", hook.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 按优先级触发 postLlmCall。
     */
    public void firePostLlmCall(AgentContext context, ChatResponse response, int iteration) {
        for (var hook : sortedHooks) {
            try {
                hook.postLlmCall(context, response, iteration);
            } catch (Exception e) {
                log.warn("Hook postLlmCall 异常 ({}): {}", hook.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 按优先级触发 beforeToolExecution，支持 Skip 短路。
     *
     * @return 最终拦截决策
     */
    public ToolInterception fireBeforeToolExecution(AgentContext context, ToolUseBlock toolCall) {
        ToolUseBlock current = toolCall;
        for (var hook : sortedHooks) {
            try {
                ToolInterception interception = hook.beforeToolExecution(context, current);
                if (interception instanceof ToolInterception.Skip) {
                    return interception;
                }
                if (interception instanceof ToolInterception.Modify m) {
                    current = new ToolUseBlock(current.id(), current.name(), m.modifiedInput());
                }
            } catch (Exception e) {
                log.warn("Hook beforeToolExecution 异常 ({}): {}",
                        hook.getClass().getSimpleName(), e.getMessage());
            }
        }
        return ToolInterception.CONTINUE;
    }

    /**
     * 按优先级触发 postToolExecution。
     */
    public void firePostToolExecution(AgentContext context, int iteration) {
        for (var hook : sortedHooks) {
            try {
                hook.postToolExecution(context, iteration);
            } catch (Exception e) {
                log.warn("Hook postToolExecution 异常 ({}): {}",
                        hook.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 按优先级触发 afterCompaction。
     */
    public void fireAfterCompaction(AgentContext context, CompactionResult result) {
        for (var hook : sortedHooks) {
            try {
                hook.afterCompaction(context, result);
            } catch (Exception e) {
                log.warn("Hook afterCompaction 异常 ({}): {}",
                        hook.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 按优先级触发 onLoopEnd。
     */
    public void fireOnLoopEnd(AgentContext context, int totalIterations) {
        for (var hook : sortedHooks) {
            try {
                hook.onLoopEnd(context, totalIterations);
            } catch (Exception e) {
                log.warn("Hook onLoopEnd 异常 ({}): {}",
                        hook.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 获取排序后的 Hook 列表（只读）。
     */
    public List<LoopHook> hooks() {
        return sortedHooks;
    }

    /**
     * 运行时新增 Hook 后重新排序。
     */
    public void addHook(LoopHook hook) {
        var newList = new ArrayList<>(sortedHooks);
        newList.add(hook);
        this.sortedHooks = sortByPriority(newList);
    }

    private static List<LoopHook> sortByPriority(List<LoopHook> hooks) {
        return hooks.stream()
                .sorted(Comparator.comparingInt(LoopHook::priority))
                .toList();
    }
}
