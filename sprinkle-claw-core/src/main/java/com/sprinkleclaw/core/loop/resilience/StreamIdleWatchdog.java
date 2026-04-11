package com.sprinkleclaw.core.loop.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 流式空闲超时检测器。
 * <p>当 SSE 事件间隔超过指定时间时，触发超时回调。</p>
 *
 * @author sprinkle
 * @since 2026/4/6
 */
public class StreamIdleWatchdog implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StreamIdleWatchdog.class);
    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(10);

    private final Duration idleTimeout;
    private final AtomicReference<Instant> lastEventTime = new AtomicReference<>(Instant.now());
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> watchFuture;
    private volatile boolean running;

    public StreamIdleWatchdog(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Thread.ofVirtual().unstarted(r);
            t.setName("stream-idle-watchdog");
            return t;
        });
    }

    /**
     * 启动看门狗。
     *
     * @param onTimeout 超时时执行的回调
     */
    public void start(Runnable onTimeout) {
        running = true;
        lastEventTime.set(Instant.now());

        watchFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;

            Duration idle = Duration.between(lastEventTime.get(), Instant.now());
            if (idle.compareTo(idleTimeout) > 0) {
                log.warn("流式空闲超时: 已 {} 未收到事件（阈值 {}）", idle, idleTimeout);
                running = false;
                onTimeout.run();
            }
        }, CHECK_INTERVAL.toMillis(), CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 心跳：收到 SSE 事件时调用，重置计时器。
     */
    public void heartbeat() {
        lastEventTime.set(Instant.now());
    }

    /**
     * 停止看门狗。
     */
    public void stop() {
        running = false;
        if (watchFuture != null) {
            watchFuture.cancel(false);
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdown();
    }

    /**
     * 是否正在运行。
     */
    public boolean isRunning() {
        return running;
    }
}
