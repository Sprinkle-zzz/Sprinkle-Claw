package icu.sprinkle.loom.gateway.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 异步缓冲审计日志。将事件写入 ConcurrentLinkedQueue，定时批量刷出到下游 {@link AuditLogger}。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class AsyncBufferedAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AsyncBufferedAuditLogger.class);

    private final AuditLogger delegate;
    private final ConcurrentLinkedQueue<AuditEvent> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;
    private final int batchSize;

    public AsyncBufferedAuditLogger(AuditLogger delegate) {
        this(delegate, 100, 5);
    }

    /**
     * @param delegate     下游审计日志实现
     * @param batchSize    批量刷出大小
     * @param flushSeconds 定时刷出间隔（秒）
     */
    public AsyncBufferedAuditLogger(AuditLogger delegate, int batchSize, int flushSeconds) {
        this.delegate = delegate;
        this.batchSize = batchSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "audit-flush");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flush, flushSeconds, flushSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void log(AuditEvent event) {
        buffer.add(event);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    @Override
    public void flush() {
        List<AuditEvent> batch = new ArrayList<>();
        AuditEvent event;
        while ((event = buffer.poll()) != null && batch.size() < batchSize * 2) {
            batch.add(event);
        }
        for (AuditEvent e : batch) {
            try {
                delegate.log(e);
            } catch (Exception ex) {
                log.warn("审计日志写入失败: {}", ex.getMessage());
            }
        }
        delegate.flush();
    }

    @Override
    public void close() {
        flush();
        scheduler.shutdown();
        delegate.close();
    }
}
