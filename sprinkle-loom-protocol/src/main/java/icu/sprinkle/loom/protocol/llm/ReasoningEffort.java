package icu.sprinkle.loom.protocol.llm;

/**
 * 推理努力程度，用于控制 LLM 在推理时的计算投入。
 *
 * <p>不同 Provider 的映射：</p>
 * <ul>
 *   <li>Anthropic：通过 {@code budgetTokens} 控制（本枚举不直接使用）</li>
 *   <li>OpenAI：映射为 {@code reasoning_effort} 参数（low/medium/high）</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/4/4
 */
public enum ReasoningEffort {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    ReasoningEffort(String value) {
        this.value = value;
    }

    /**
     * 返回 API 传输用的小写字符串值。
     */
    public String value() {
        return value;
    }
}
