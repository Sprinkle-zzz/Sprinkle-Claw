package icu.sprinkle.loom.ext.identity;

import icu.sprinkle.loom.core.context.AgentContext;
import icu.sprinkle.loom.core.context.CompactionResult;
import icu.sprinkle.loom.core.loop.LoopHook;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import icu.sprinkle.loom.protocol.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 身份重注入 Hook：在上下文压缩完成后，将 Agent 身份描述注入到消息开头。
 *
 * <p>上下文压缩后，消息历史被替换为摘要，模型可能忘记自己的身份与角色。
 * 此 Hook 在 {@link #afterCompaction} 中注入身份描述块，确保模型在压缩后
 * 仍能保持身份认知。</p>
 *
 * <p>注入格式：</p>
 * <pre>
 *   [User]      &lt;identity&gt;{identityPrompt}&lt;/identity&gt;
 *   [Assistant] Understood. I'll continue with this identity.
 *   ... (压缩后的消息历史)
 * </pre>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class IdentityReinjectHook implements LoopHook {

    private static final Logger log = LoggerFactory.getLogger(IdentityReinjectHook.class);

    private final String identityPrompt;

    /**
     * 创建身份重注入 Hook。
     *
     * @param identityPrompt 身份描述文本（空或 null 则不注入）
     */
    public IdentityReinjectHook(String identityPrompt) {
        this.identityPrompt = identityPrompt;
    }

    @Override
    public void afterCompaction(AgentContext context, CompactionResult result) {
        if (identityPrompt == null || identityPrompt.isBlank()) {
            return;
        }

        // 在压缩后的消息开头注入身份确认对话（先插入 assistant 回复，再插入 user 身份块，
        // 因为 prependMessage 是在头部插入，后插入的会排在最前面）
        context.prependMessage(new Message.AssistantMessage(
                List.of(new ContentBlock.TextBlock("Understood. I'll continue with this identity.")),
                StopReason.END_TURN
        ));
        context.prependMessage(Message.UserMessage.of(
                "<identity>" + identityPrompt + "</identity>"
        ));

        String preview = identityPrompt.length() > 50
                ? identityPrompt.substring(0, 50) + "..."
                : identityPrompt;
        log.debug("Identity reinjected after compaction ({}): {}", result.type(), preview);
    }
}
