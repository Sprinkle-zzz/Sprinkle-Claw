package icu.sprinkle.loom.examples;

import icu.sprinkle.loom.bootstrap.LoomBuilder;
import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.AgentResult;

import java.nio.file.Path;
import java.util.Map;

/**
 * 编码 Agent 示例：一键启用文件读写和 bash 工具。
 *
 * <p>与 {@link MinimalAgent} 对比：同一 SDK，
 * 通过 {@code enableCodingTools()} 切换为编码场景。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class CodingAgent {

    public static void main(String[] args) {
        try (Loom claw = LoomBuilder.create()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                // 关闭 deepseek-v4-flash 的思考模式（vendor 私有字段，按 DeepSeek 文档透传）
                .customParameter("thinking", Map.of("type", "disabled"))
                .workingDirectory(Path.of("."))
                .enableCodingTools()
                .systemPrompt("你是一个 Java 编程助手，帮助用户读取和修改代码。")
                .build()) {

            AgentResult result = claw.run("读取当前目录下的 pom.xml，告诉我项目名称和版本号");
            System.out.println(result.output());
            System.out.printf("迭代次数: %d, 工具调用: %d%n",
                    result.totalIterations(), result.toolExecutions().size());
        }
    }
}
