package com.sprinkleclaw.examples;

import com.sprinkleclaw.llm.LlmProvider;
import com.sprinkleclaw.llm.LlmProviderFactory;
import com.sprinkleclaw.llm.LlmConfig;
import com.sprinkleclaw.workflow.agent.Agent;
import com.sprinkleclaw.workflow.agent.AgentFactory;
import com.sprinkleclaw.workflow.agent.UserMessage;
import com.sprinkleclaw.workflow.orchestration.ErrorPolicy;
import com.sprinkleclaw.workflow.orchestration.WorkflowStep;
import com.sprinkleclaw.workflow.orchestration.sequential.SequentialWorkflow;

import java.util.List;
import java.util.ServiceLoader;

/**
 * 多 Agent 协作示例：Researcher + Writer 通过 SequentialWorkflow 串联。
 *
 * <p>流程：用户输入主题 → Researcher 提炼要点 → Writer 撰写报告。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class MultiAgentCollaboration {

    @Agent(systemPrompt = "你是一位资深研究员，擅长从主题中提炼 3-5 个核心要点。只输出要点列表，不要其他内容。")
    public interface Researcher {
        String research(@UserMessage String topic);
    }

    @Agent(systemPrompt = "你是一位技术作家，根据给定的要点列表撰写一篇 200 字左右的中文简报。")
    public interface Writer {
        String write(@UserMessage String keyPoints);
    }

    public static void main(String[] args) {
        LlmProvider provider = createProvider();

        Researcher researcher = AgentFactory.create(Researcher.class, provider);
        Writer writer = AgentFactory.create(Writer.class, provider);

        WorkflowStep<String, String> researchStep =
                WorkflowStep.of("research", researcher::research);
        WorkflowStep<String, String> writeStep =
                WorkflowStep.of("write", writer::write);

        var workflow = new SequentialWorkflow<String, String>(
                List.of(researchStep, writeStep), ErrorPolicy.FAIL_FAST);

        var result = workflow.run("Java 21 虚拟线程在高并发 Web 服务中的最佳实践");

        if (result.success()) {
            System.out.println("=== 生成报告 ===");
            System.out.println(result.output());
        } else {
            System.err.println("Workflow 执行失败: " + result.error().getMessage());
        }

        System.out.println("\n=== 步骤耗时 ===");
        result.stepResults().forEach(step ->
                System.out.printf("  %s: %dms%n", step.stepName(), step.duration().toMillis()));
    }

    private static LlmProvider createProvider() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        LlmConfig config = LlmConfig.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                .build();
        return ServiceLoader.load(LlmProviderFactory.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No LlmProviderFactory found on classpath"))
                .create(config);
    }
}
