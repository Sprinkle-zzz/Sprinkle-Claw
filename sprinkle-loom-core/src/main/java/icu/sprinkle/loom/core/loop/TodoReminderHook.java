package icu.sprinkle.loom.core.loop;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.context.CompactionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TodoWrite Nag Reminder 钩子。
 *
 * <p>当模型连续多轮（默认 3 轮）未调用 {@code todo_write} 更新任务状态时，
 * 框架主动注入提醒消息，引导模型维护 todo 列表。</p>
 *
 * <h3>与 AgentLoop 的约定</h3>
 * <ul>
 *   <li>每轮迭代开始时将 {@code __round_used_todo_write} 设为 {@code false}</li>
 *   <li>工具执行阶段若实际执行了 {@code todo_write}，设为 {@code true}</li>
 *   <li>{@code postToolExecution} 钩子中检查此标记决定是否递增计数</li>
 * </ul>
 *
 * <h3>压缩后恢复</h3>
 * <p>TodoState 存储在 {@code AgentContext.attributes} 中，不受消息压缩影响。
 * {@code afterCompaction} 钩子将当前 todo 状态以 {@code <todo-snapshot>} 格式
 * 注入到 reminder 中，确保模型在压缩后仍能看到当前任务列表。</p>
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public final class TodoReminderHook implements LoopHook {

    private static final Logger log = LoggerFactory.getLogger(TodoReminderHook.class);

    /**
     * AgentLoop 在每轮迭代中设置的标记键。
     */
    public static final String ROUND_USED_TODO_WRITE_KEY = "__round_used_todo_write";

    /**
     * TodoState 在 attributes 中的存储键（与 TodoState.CONTEXT_KEY 保持一致）。
     */
    private static final String TODO_STATE_KEY = "todo_state";

    private final int nagThreshold;
    private int roundsSinceUpdate = 0;

    /**
     * 创建 Todo 提醒钩子。
     *
     * @param nagThreshold 连续多少轮未更新后触发提醒
     */
    public TodoReminderHook(int nagThreshold) {
        this.nagThreshold = nagThreshold;
    }

    @Override
    public void postToolExecution(AgentContext context, int iteration) {
        if (Boolean.TRUE.equals(context.getAttribute(ROUND_USED_TODO_WRITE_KEY))) {
            roundsSinceUpdate = 0;
            return;
        }

        roundsSinceUpdate++;

        if (roundsSinceUpdate >= nagThreshold && hasTodoState(context)) {
            context.addReminder(
                    "<reminder>You have an active todo list. "
                            + "Please update it to reflect your current progress. "
                            + "Mark completed items and update in-progress status.</reminder>");
            roundsSinceUpdate = 0;
            log.debug("Todo nag reminder 已注入（连续 {} 轮未更新）", nagThreshold);
        }
    }

    @Override
    public void afterCompaction(AgentContext context, CompactionResult result) {
        Object stateObj = context.getAttribute(TODO_STATE_KEY);
        if (stateObj != null) {
            try {
                @SuppressWarnings("unchecked")
                var formatMethod = stateObj.getClass().getMethod("formatAsText");
                var itemsMethod = stateObj.getClass().getMethod("items");
                var items = (java.util.List<?>) itemsMethod.invoke(stateObj);

                if (!items.isEmpty()) {
                    String text = (String) formatMethod.invoke(stateObj);
                    context.addReminder(
                            "<todo-snapshot>\n" + text + "\n</todo-snapshot>");
                    log.debug("压缩后 todo snapshot 已注入（{} 项）", items.size());
                }
            } catch (Exception e) {
                log.warn("读取 TodoState 失败", e);
            }
        }
        roundsSinceUpdate = 0;
    }

    /**
     * 检查是否存在非空的 TodoState。
     */
    private boolean hasTodoState(AgentContext context) {
        Object stateObj = context.getAttribute(TODO_STATE_KEY);
        if (stateObj == null) {
            return false;
        }
        try {
            var itemsMethod = stateObj.getClass().getMethod("items");
            var items = (java.util.List<?>) itemsMethod.invoke(stateObj);
            return !items.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
