package icu.sprinkle.loom.examples;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.bootstrap.LoomBuilder;
import icu.sprinkle.loom.core.loop.event.AgentEvent;
import icu.sprinkle.loom.stream.FlowStreams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式 Agent 示例：逐 token 打印 LLM 输出到控制台。
 *
 * <p>通过订阅 {@link Loom#runStreaming(String)} 返回的 {@link Flow.Publisher}
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
 * mvn compile exec:java -pl sprinkle-loom-examples -Dexec.mainClass=icu.sprinkle.loom.examples.StreamingAgent
 * }</pre>
 *
 * @author sprinkle
 * @since 2026/4/27
 */
public class StreamingAgent {

    public static void main(String[] args) throws InterruptedException {
        try (Loom loom = LoomBuilder.create()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .baseUrl("https://api.deepseek.com/v1")
                .model("deepseek-v4-flash")
                .systemPrompt("你是一个友好的助手，请用中文回答。")
                .build()) {

            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean inThinking = new AtomicBoolean(false);

            FlowStreams.subscribe(loom.runStreaming("写一首关于秋天的五言绝句。"))
                    .onNext(event -> handleEvent(event, inThinking))
                    .onError(throwable -> {
                        System.err.println("\n[流式错误] " + throwable.getMessage());
                        done.countDown();
                    })
                    .onComplete(done::countDown)
                    .start();

            done.await();
        }
    }

    private static void handleEvent(AgentEvent event, AtomicBoolean inThinking) {
        switch (event) {
            case AgentEvent.ThinkingToken t -> {
                if (inThinking.compareAndSet(false, true)) {
                    System.out.print("\n[思考] ");
                }
                // ANSI 灰色显示思考流，与最终输出区分
                System.out.print("\u001B[90m" + t.token() + "\u001B[0m");
            }
            case AgentEvent.LlmToken t -> {
                if (inThinking.compareAndSet(true, false)) {
                    System.out.print("\n[回复] ");
                }
                System.out.print(t.token());
            }
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
}
