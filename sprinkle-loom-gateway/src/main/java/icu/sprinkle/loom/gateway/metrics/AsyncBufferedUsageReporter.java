package icu.sprinkle.loom.gateway.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 异步缓冲用量上报器。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class AsyncBufferedUsageReporter implements UsageReporter {

    private static final Logger log = LoggerFactory.getLogger(AsyncBufferedUsageReporter.class);

    private final UsageReporter delegate;
    private final ConcurrentLinkedQueue<UsageEvent> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;
    private final int batchSize;

    public AsyncBufferedUsageReporter(UsageReporter delegate) {
        this(delegate, 50, 10);
    }

    public AsyncBufferedUsageReporter(UsageReporter delegate, int batchSize, int flushSeconds) {
        this.delegate = delegate;
        this.batchSize = batchSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "usage-flush");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flush, flushSeconds, flushSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void report(UsageEvent event) {
        buffer.add(event);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    @Override
    public void flush() {
        List<UsageEvent> batch = new ArrayList<>();
        UsageEvent event;
        while ((event = buffer.poll()) != null && batch.size() < batchSize * 2) {
            batch.add(event);
        }
        for (UsageEvent e : batch) {
            try {
                delegate.report(e);
            } catch (Exception ex) {
                log.warn("用量上报失败: {}", ex.getMessage());
            }
        }
        delegate.flush();
    }
}
