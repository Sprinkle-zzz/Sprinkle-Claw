package icu.sprinkle.loom.core.loop;

import java.util.Map;

/**
 * 工具拦截决策，由 {@link LoopHook#beforeToolExecution} 返回，控制工具调用的执行流程。
 *
 * <ul>
 *   <li>{@link Continue} — 允许工具正常执行</li>
 *   <li>{@link Skip} — 跳过此工具调用，返回错误信息给 LLM</li>
 *   <li>{@link Modify} — 修改工具输入参数后再执行</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/22
 */
public sealed interface ToolInterception {

    /**
     * 允许工具正常执行。
     */
    record Continue() implements ToolInterception {
    }

    /**
     * 跳过此工具调用。
     *
     * @param reason 跳过原因（会作为错误信息反馈给 LLM）
     */
    record Skip(String reason) implements ToolInterception {
    }

    /**
     * 修改工具输入参数后再执行。
     *
     * @param modifiedInput 修改后的输入参数
     */
    record Modify(Map<String, Object> modifiedInput) implements ToolInterception {
    }

    /**
     * 预定义的"继续"实例，避免重复创建。
     */
    ToolInterception CONTINUE = new Continue();
}
