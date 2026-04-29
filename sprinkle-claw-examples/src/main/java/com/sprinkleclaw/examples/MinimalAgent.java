package com.sprinkleclaw.examples;

import com.sprinkleclaw.bootstrap.ClawBuilder;
import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.core.AgentResult;

/**
 * 最小 Agent 示例：零工具、纯对话。
 *
 * <pre>{@code
 * set DEEPSEEK_API_KEY=sk-...
 * mvn compile exec:java -pl sprinkle-claw-examples -Dexec.mainClass=com.sprinkleclaw.examples.MinimalAgent
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class MinimalAgent {

    public static void main(String[] args) {
        try (Claw claw = ClawBuilder.create()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                .systemPrompt("你是一个友好的助手，请用中文回答。")
                .build()) {

            AgentResult result = claw.run("你好！请用一句话介绍一下你自己。");
            System.out.println(result.output());
        }
    }
}
