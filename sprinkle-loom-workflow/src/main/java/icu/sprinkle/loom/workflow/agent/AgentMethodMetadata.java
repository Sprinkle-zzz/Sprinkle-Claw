package icu.sprinkle.loom.workflow.agent;

import com.fasterxml.jackson.databind.JsonNode;
import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.workflow.agent.structured.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 对一个声明式 Agent 方法的元数据解析结果（反射解析一次，缓存复用）。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class AgentMethodMetadata {

    private final Method method;
    private final String systemPromptTemplate;
    private final String userMessageTemplate;
    private final List<ParamInfo> params;
    private final Type returnType;
    private final JsonNode returnSchema;
    private final StructuredOutputStrategy strategy;
    private final int maxCorrectionRetries;
    private final int maxIterations;
    private final boolean hasTools;

    record ParamInfo(String name, int index, boolean isUserMessage, String description) {}

    AgentMethodMetadata(Method method, Agent agentAnnotation) {
        this.method = method;
        this.returnType = method.getGenericReturnType();
        this.maxIterations = agentAnnotation.maxIterations();
        this.strategy = agentAnnotation.outputStrategy();
        this.maxCorrectionRetries = agentAnnotation.maxCorrectionRetries();
        this.hasTools = false; // 在 AgentFactory 层面根据 config.tools() 决定

        // 解析 system prompt
        SystemMessage methodSystem = method.getAnnotation(SystemMessage.class);
        this.systemPromptTemplate = methodSystem != null
                ? PromptResources.resolve(method.getDeclaringClass(), methodSystem.value(), methodSystem.fromResource())
                : "";

        // 解析 user message 模板
        UserMessage methodUser = method.getAnnotation(UserMessage.class);
        this.userMessageTemplate = methodUser != null
                ? PromptResources.resolve(method.getDeclaringClass(), methodUser.value(), methodUser.fromResource())
                : "";

        // 解析参数信息
        this.params = parseParams(method);

        // 生成返回类型 schema（非 String 时）
        if (returnType != String.class && returnType != void.class) {
            this.returnSchema = JsonSchemaGenerator.fromType(returnType);
        } else {
            this.returnSchema = null;
        }
    }

    private static List<ParamInfo> parseParams(Method method) {
        Parameter[] parameters = method.getParameters();
        List<ParamInfo> result = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            boolean isUserMessage = p.isAnnotationPresent(UserMessage.class);
            Description desc = p.getAnnotation(Description.class);
            String description = desc != null ? desc.value() : "";
            String name = p.getName();
            result.add(new ParamInfo(name, i, isUserMessage, description));
        }
        return List.copyOf(result);
    }

    /**
     * 渲染 user message。
     */
    String renderUserMessage(Object[] args) {
        // 模式 A：单个 @UserMessage 参数，无模板
        if (userMessageTemplate.isEmpty() && params.size() == 1) {
            return String.valueOf(args[0]);
        }

        // 模式 A2：有 @UserMessage 参数标注，无方法级模板
        if (userMessageTemplate.isEmpty()) {
            var sb = new StringBuilder();
            for (var p : params) {
                if (p.isUserMessage()) {
                    sb.append(String.valueOf(args[p.index()]));
                }
            }
            if (!sb.isEmpty()) {
                appendDescriptions(sb, args);
                return sb.toString();
            }
            // fallback：拼接所有参数
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append("\n");
                sb.append(String.valueOf(args[i]));
            }
            return sb.toString();
        }

        // 模式 B/C：模板替换
        String rendered = userMessageTemplate;
        for (var p : params) {
            rendered = rendered.replace("{" + p.name() + "}", String.valueOf(args[p.index()]));
        }
        appendDescriptions(new StringBuilder(rendered), args);
        return rendered;
    }

    private void appendDescriptions(StringBuilder sb, Object[] args) {
        boolean hasDescriptions = params.stream().anyMatch(p -> !p.description().isEmpty());
        if (hasDescriptions) {
            sb.append("\n\nParameters:");
            for (var p : params) {
                if (!p.description().isEmpty()) {
                    sb.append("\n- ").append(p.name()).append(": ").append(args[p.index()])
                            .append("  [").append(p.description()).append("]");
                }
            }
        }
    }

    /**
     * 渲染 system prompt。
     */
    String renderSystemPrompt(String agentLevelPrompt) {
        var sb = new StringBuilder();
        if (!agentLevelPrompt.isEmpty()) {
            sb.append(agentLevelPrompt);
        }
        if (!systemPromptTemplate.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(systemPromptTemplate);
        }
        // 非 tool 模式下，注入返回类型 schema
        if (returnSchema != null && resolveEffectiveStrategy(false) != StructuredOutputStrategy.GENERATE_RESPONSE_TOOL) {
            sb.append("\n\n# Output Format\n");
            sb.append("You MUST respond with a JSON object matching this schema:\n");
            sb.append("```json\n").append(returnSchema.toPrettyString()).append("\n```\n");
            sb.append("Respond ONLY with the JSON object, no other text.");
        }
        return sb.toString();
    }

    StructuredOutputStrategy resolveEffectiveStrategy(boolean supportsToolChoice) {
        if (strategy == StructuredOutputStrategy.AUTO) {
            return supportsToolChoice
                    ? StructuredOutputStrategy.GENERATE_RESPONSE_TOOL
                    : StructuredOutputStrategy.PROMPT_JSON;
        }
        return strategy;
    }

    Method method() { return method; }
    Type returnType() { return returnType; }
    JsonNode returnSchema() { return returnSchema; }
    boolean isStructuredOutput() { return returnSchema != null; }
    int maxCorrectionRetries() { return maxCorrectionRetries; }
    int maxIterations() { return maxIterations; }
    StructuredOutputStrategy strategy() { return strategy; }
}
