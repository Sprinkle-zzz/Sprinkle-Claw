package icu.sprinkle.loom.examples;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.bootstrap.LoomBuilder;
import icu.sprinkle.loom.core.AgentResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 业务嵌入 Agent 示例：客服场景，无文件/bash 工具。
 *
 * <p>展示：
 * <ul>
 *   <li>通过 {@code addSkill()} 注册业务 Skill</li>
 *   <li>通过 {@code systemPrompt()} 设定角色</li>
 *   <li>通过 {@code chatAsync()} 异步多轮对话</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public class CustomerServiceAgent {

    public static void main(String[] args) throws Exception {
        try (Loom loom = LoomBuilder.create()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                .systemPrompt("""
                        你是一位专业的电商客服，负责处理订单查询和退款申请。
                        始终保持礼貌，优先安抚用户情绪，然后解决问题。
                        如果用户要求退款，先询问订单号。
                        """)
                .addSkill("查询订单", "根据订单号查询订单状态",
                        List.of("订单", "查询"),
                        """
                        当用户提供订单号时，回复该订单的模拟状态。
                        格式：订单 {订单号} 状态为「已发货」，预计明天送达。
                        """)
                .addSkill("退款政策", "说明退款政策和流程",
                        """
                        退款政策：
                        - 7天无理由退款（未拆封）
                        - 15天质量问题退换
                        - 退款将在3-5个工作日内到账
                        """)
                .build()) {

            // 异步多轮对话
            CompletableFuture<AgentResult> future = loom.chatAsync("你好，我想退款");
            AgentResult r1 = future.get();
            System.out.println("[客服] " + r1.output());

            AgentResult r2 = loom.chat("订单号是 20260425001");
            System.out.println("[客服] " + r2.output());
        }
    }
}
