package com.sprinkleclaw.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 共享 {@link HttpClient} 实例，所有 LLM Provider 复用。
 *
 * <p>HTTP/2 多路复用减少连接数，Virtual Thread executor 兼容并发模型。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
public final class SharedHttpClient {

    private static volatile HttpClient instance;

    /**
     * 获取共享 HttpClient 实例。首次调用时惰性创建。
     */
    public static HttpClient get() {
        if (instance == null) {
            synchronized (SharedHttpClient.class) {
                if (instance == null) {
                    instance = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)
                            .connectTimeout(Duration.ofSeconds(10))
                            .executor(Executors.newVirtualThreadPerTaskExecutor())
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return instance;
    }

    /**
     * 替换共享实例（测试或特殊网络环境）。
     */
    public static void override(HttpClient custom) {
        instance = custom;
    }

    private SharedHttpClient() {
    }
}
