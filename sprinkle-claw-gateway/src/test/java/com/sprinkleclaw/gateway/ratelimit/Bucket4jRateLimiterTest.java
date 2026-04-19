package com.sprinkleclaw.gateway.ratelimit;

import com.sprinkleclaw.gateway.tenant.TenantPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bucket4jRateLimiterTest {

    @Test
    void allowsRequestsUpToPlanCapacity() {
        Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        for (int i = 0; i < TenantPlan.FREE.requestsPerMinute(); i++) {
            assertTrue(limiter.tryConsume("tenant-1", TenantPlan.FREE).allowed(),
                    "request " + i + " should be allowed");
        }
    }

    @Test
    void rejectsWhenCapacityExhausted() {
        Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        for (int i = 0; i < TenantPlan.FREE.requestsPerMinute(); i++) {
            limiter.tryConsume("tenant-2", TenantPlan.FREE);
        }
        RateLimitResult result = limiter.tryConsume("tenant-2", TenantPlan.FREE);
        assertFalse(result.allowed());
        assertTrue(result.retryAfter() > 0);
    }

    @Test
    void differentTenantsUseIndependentBuckets() {
        Bucket4jRateLimiter limiter = new Bucket4jRateLimiter();
        for (int i = 0; i < TenantPlan.FREE.requestsPerMinute(); i++) {
            limiter.tryConsume("tenant-a", TenantPlan.FREE);
        }
        assertTrue(limiter.tryConsume("tenant-b", TenantPlan.FREE).allowed());
    }
}
