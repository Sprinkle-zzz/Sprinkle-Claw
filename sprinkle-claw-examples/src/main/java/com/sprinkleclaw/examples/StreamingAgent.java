package com.sprinkleclaw.examples;

import com.sprinkleclaw.bootstrap.Claw;
import com.sprinkleclaw.bootstrap.ClawBuilder;
import com.sprinkleclaw.core.loop.event.AgentEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

/**
 * 流式 Agent 示例：逐 token 打印 LLM 输出到控制台。
 *
 * <p>通过订阅 {@link Claw#runStreaming(String)} 返回的 {@link Flow.Publisher}
 * 实时处理 17 种 {@link AgentEvent} 事件类型，常见用法：</p>
 * <ul>
 *   <li>{@link AgentEvent.LlmToken} —— 逐字符渲染对话内容</li>
 *   <li>{@link AgentEvent.ToolStart} / {@link AgentEvent.ToolEnd} —— 显示工具调用进度</li>
 *   <li>{@link AgentEvent.AgentComplete} —— 终态汇总</li>
 *   <li>{@link AgentEvent.AgentError} —— 错误终态</li>
 * </ul>
 *
 * <pre>{@code
 * set DEEPSEEK_API_KEY=sk-...
 * mvn compile exec:java -pl sprinkle-claw-examples -Dexec.mainClass=com.sprinkleclaw.examples.StreamingAgent
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/27
 */
public class StreamingAgent {

    public static void main(String[] args) throws InterruptedException {
        try (Claw claw = ClawBuilder.create()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                .systemPrompt("你是一个友好的助手，请用中文回答。")
                .build()) {

            CountDownLatch done = new CountDownLatch(1);

            claw.runStreaming("写一首关于秋天的五言绝句。").subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(AgentEvent event) {
                    switch (event) {
                        case AgentEvent.LlmToken t -> System.out.print(t.token());
                        case AgentEvent.ToolStart s ->
                                System.out.printf("%n[工具调用: %s]%n", s.toolName());
                        case AgentEvent.ToolEnd e ->
                                System.out.printf("%n[工具完成: %s, 耗时 %d ms]%n",
                                        e.toolName(), e.duration().toMillis());
                        case AgentEvent.AgentComplete c ->
                                System.out.printf("%n%n[执行完成 — 迭代 %d 轮]%n",
                                        c.result().totalIterations());
                        default -> {
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    System.err.println("\n[流式错误] " + throwable.getMessage());
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });

            done.await();
        }
    }
}
