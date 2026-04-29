package icu.sprinkle.loom.workflow.agent.structured;

/**
 * 结构化输出解析结果（三态）。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
public sealed interface ParseResult<T> {

    record Success<T>(T value) implements ParseResult<T> {}
    record Retry<T>(String correctionPrompt) implements ParseResult<T> {}
    record Fatal<T>(String error) implements ParseResult<T> {}

    static <T> ParseResult<T> success(T v) { return new Success<>(v); }
    static <T> ParseResult<T> retry(String p) { return new Retry<>(p); }
    static <T> ParseResult<T> fatal(String e) { return new Fatal<>(e); }
}
