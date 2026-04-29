package icu.sprinkle.loom.ext.guardrails;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.loop.LoopHook;
import icu.sprinkle.loom.core.loop.ToolInterception;
import icu.sprinkle.loom.protocol.message.ContentBlock.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * 信任边界过滤器：标记不可信来源内容，检测工具输入中的注入尝试。
 *
 * <p>作为 {@link LoopHook} 在 {@code beforeToolExecution} 阶段生效：</p>
 * <ul>
 *   <li>对文件读取、网络请求等工具的输入进行注入模式检测</li>
 *   <li>使用 {@link InputSanitizer} 检查工具输入参数中的可疑内容</li>
 *   <li>检测到注入时记录警告日志（默认不拦截，仅告警）</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/4/4
 */
public final class TrustBoundaryFilter implements LoopHook {

    private static final Logger log = LoggerFactory.getLogger(TrustBoundaryFilter.class);

    /**
     * 需要对输入做注入检测的工具名
     */
    private static final Set<String> TOOLS_WITH_EXTERNAL_INPUT = Set.of(
            "bash", "read_file", "write_file", "edit_file"
    );

    private final InputSanitizer sanitizer;
    private final boolean blockOnDetection;

    /**
     * 创建信任边界过滤器（默认仅告警，不拦截）。
     */
    public TrustBoundaryFilter() {
        this(new InputSanitizer(), false);
    }

    /**
     * 创建信任边界过滤器。
     *
     * @param sanitizer        输入检查器
     * @param blockOnDetection 检测到注入时是否拦截工具调用
     */
    public TrustBoundaryFilter(InputSanitizer sanitizer, boolean blockOnDetection) {
        this.sanitizer = sanitizer;
        this.blockOnDetection = blockOnDetection;
    }

    @Override
    public ToolInterception beforeToolExecution(AgentContext context, ToolUseBlock toolCall) {
        if (!TOOLS_WITH_EXTERNAL_INPUT.contains(toolCall.name())) {
            return ToolInterception.CONTINUE;
        }

        // 对工具输入的所有字符串值做注入检测
        Map<String, Object> input = toolCall.input();
        if (input == null || input.isEmpty()) {
            return ToolInterception.CONTINUE;
        }

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getValue() instanceof String value) {
                InputSanitizer.SanitizeResult result = sanitizer.sanitize(value);
                if (!result.passed()) {
                    log.warn("[TrustBoundary] Tool '{}' parameter '{}' flagged: {}",
                            toolCall.name(), entry.getKey(), result.warnings());

                    if (blockOnDetection) {
                        return new ToolInterception.Skip(
                                "Tool input blocked by trust boundary filter: "
                                        + String.join("; ", result.warnings()));
                    }
                }
            }
        }

        return ToolInterception.CONTINUE;
    }
}
