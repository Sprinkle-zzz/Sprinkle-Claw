package icu.sprinkle.loom.workflow.agent;

/**
 * 结构化输出策略，决定如何从 LLM 获取类型安全的返回值。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public enum StructuredOutputStrategy {

    /** 自动：优先 GENERATE_RESPONSE_TOOL（如果 Provider 支持 toolChoice），否则降级 PROMPT_JSON */
    AUTO,

    /** 强制工具调用模式：注册 generate_response 工具 + forced tool_choice */
    GENERATE_RESPONSE_TOOL,

    /** 纯 prompt + JSON Schema 注入 + 自纠正解析 */
    PROMPT_JSON
}
