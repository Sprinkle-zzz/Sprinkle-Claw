package icu.sprinkle.loom.examples;

import icu.sprinkle.loom.bootstrap.LoomBuilder;
import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.AgentResult;

/**
 * 最小 Agent 示例：零工具、纯对话。
 *
 * <pre>{@code
 * set DEEPSEEK_API_KEY=sk-...
 * mvn compile exec:java -pl sprinkle-loom-examples -Dexec.mainClass=icu.sprinkle.loom.examples.MinimalAgent
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class MinimalAgent {

    public static void main(String[] args) {
        try (Loom claw = LoomBuilder.create()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                .systemPrompt("你是一个友好的助手，请用中文回答。")
                .build()) {

            AgentResult result = claw.run("你好！请用一句话介绍一下你自己。");

            String thinking = result.thinking();
            if (!thinking.isEmpty()) {
                System.out.println("=== 思考过程 ===");
                System.out.println(thinking);
                System.out.println("=== 最终回复 ===");
            }
            System.out.println(result.output());
        }
    }
}
