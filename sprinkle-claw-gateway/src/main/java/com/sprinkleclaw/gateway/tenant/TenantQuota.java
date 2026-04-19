package com.sprinkleclaw.gateway.tenant;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 租户日配额管理。每个租户维护当日已消耗 token 总量。
 * <p>日期切换时自动重置计数。</p>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class TenantQuota {

    private final ConcurrentHashMap<String, DailyCounter> counters = new ConcurrentHashMap<>();

    /**
     * 尝试消费 token 配额。
     *
     * @param tenantId 租户标识
     * @param plan     租户计划
     * @param tokens   本次消耗的 token 数
     * @return true 如果未超限
     */
    public boolean tryConsume(String tenantId, TenantPlan plan, long tokens) {
        DailyCounter counter = counters.compute(tenantId, (k, v) -> {
            if (v == null || !v.date.equals(LocalDate.now())) {
                return new DailyCounter(LocalDate.now(), new AtomicLong(0));
            }
            return v;
        });
        long current = counter.used.addAndGet(tokens);
        return current <= plan.dailyTokenQuota();
    }

    /**
     * 查询租户当日已使用量。
     */
    public long usedTokens(String tenantId) {
        DailyCounter counter = counters.get(tenantId);
        if (counter == null || !counter.date.equals(LocalDate.now())) {
            return 0;
        }
        return counter.used.get();
    }

    private record DailyCounter(LocalDate date, AtomicLong used) {
    }
}
