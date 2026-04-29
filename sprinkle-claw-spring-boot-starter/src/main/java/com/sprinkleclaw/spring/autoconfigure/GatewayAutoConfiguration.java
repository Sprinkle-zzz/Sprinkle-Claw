package com.sprinkleclaw.spring.autoconfigure;

import com.sprinkleclaw.gateway.acl.AccessControlList;
import com.sprinkleclaw.gateway.acl.AclFilter;
import com.sprinkleclaw.gateway.audit.AsyncBufferedAuditLogger;
import com.sprinkleclaw.gateway.audit.AuditFilter;
import com.sprinkleclaw.gateway.audit.AuditLogger;
import com.sprinkleclaw.gateway.auth.ApiKeyAuthProvider;
import com.sprinkleclaw.gateway.auth.ApiKeyStore;
import com.sprinkleclaw.gateway.auth.AuthFilter;
import com.sprinkleclaw.gateway.auth.AuthProvider;
import com.sprinkleclaw.gateway.auth.InMemoryApiKeyStore;
import com.sprinkleclaw.gateway.filter.GatewayFilter;
import com.sprinkleclaw.gateway.filter.GatewayFilterChain;
import com.sprinkleclaw.gateway.metrics.AsyncBufferedUsageReporter;
import com.sprinkleclaw.gateway.metrics.TokenMeteringFilter;
import com.sprinkleclaw.gateway.metrics.UsageEvent;
import com.sprinkleclaw.gateway.metrics.UsageReporter;
import com.sprinkleclaw.gateway.ratelimit.Bucket4jRateLimiter;
import com.sprinkleclaw.gateway.ratelimit.RateLimitFilter;
import com.sprinkleclaw.gateway.ratelimit.RateLimiter;
import com.sprinkleclaw.gateway.security.KeywordInjectionDetector;
import com.sprinkleclaw.gateway.security.OutputValidator;
import com.sprinkleclaw.gateway.security.PromptInjectionGuard;
import com.sprinkleclaw.gateway.security.SensitivePatternRule;
import com.sprinkleclaw.gateway.tenant.TenantFilter;
import com.sprinkleclaw.gateway.tenant.TenantQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway 过滤器链自动配置。仅当 {@code sprinkle-claw.gateway.enabled=true} 时生效。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
@AutoConfiguration(after = SprinkleClawAutoConfiguration.class)
@ConditionalOnProperty(prefix = "sprinkle-claw.gateway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SprinkleClawProperties.class)
public class GatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyStore apiKeyStore(SprinkleClawProperties properties) {
        InMemoryApiKeyStore store = new InMemoryApiKeyStore();
        for (SprinkleClawProperties.Auth.Key k : properties.getGateway().getAuth().getKeys()) {
            store.register(new ApiKeyStore.ApiKeyEntry(
                    k.getKey(), k.getTenant(), k.getUserId(), k.getPlan()));
        }
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthProvider apiKeyAuthProvider(ApiKeyStore store) {
        return new ApiKeyAuthProvider(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter() {
        return new Bucket4jRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantQuota tenantQuota() {
        return new TenantQuota();
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessControlList accessControlList(SprinkleClawProperties properties) {
        SprinkleClawProperties.Acl aclCfg = properties.getGateway().getAcl();
        AccessControlList.DefaultPolicy policy = "deny".equalsIgnoreCase(aclCfg.getDefaultPolicy())
                ? AccessControlList.DefaultPolicy.DENY
                : AccessControlList.DefaultPolicy.ALLOW;
        AccessControlList acl = new AccessControlList(policy);
        aclCfg.getIpBlacklist().forEach(acl::addToBlacklist);
        aclCfg.getIpWhitelist().forEach(acl::addToWhitelist);
        return acl;
    }

    @Bean
    @ConditionalOnMissingBean
    public UsageReporter usageReporter() {
        return new AsyncBufferedUsageReporter(event -> log.debug("Usage: tenant={}, user={}, input={}, output={}",
                event.tenantId(), event.userId(),
                event.inputTokens(), event.outputTokens()));
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogger.class)
    @ConditionalOnProperty(prefix = "sprinkle-claw.gateway.audit", name = "enabled", havingValue = "true")
    public AuditLogger auditLogger() {
        return new AsyncBufferedAuditLogger(event ->
                log.info("Audit: {}", event));
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayFilterChain gatewayFilterChain(
            SprinkleClawProperties properties,
            AccessControlList acl,
            List<AuthProvider> authProviders,
            RateLimiter rateLimiter,
            TenantQuota tenantQuota,
            UsageReporter usageReporter,
            ObjectProvider<AuditLogger> auditLoggerProvider) {

        SprinkleClawProperties.Gateway gw = properties.getGateway();
        List<GatewayFilter> filters = new ArrayList<>();

        filters.add(new AclFilter(acl));
        filters.add(new AuthFilter(authProviders));
        filters.add(new TenantFilter());

        if (gw.getRateLimit().isEnabled()) {
            filters.add(new RateLimitFilter(rateLimiter));
        }

        if (gw.getSecurity().isPromptInjectionGuardEnabled()) {
            filters.add(new PromptInjectionGuard(new KeywordInjectionDetector()));
        }

        AuditLogger auditLogger = auditLoggerProvider.getIfAvailable();
        if (auditLogger != null) {
            filters.add(new AuditFilter(auditLogger));
        }

        filters.add(new TokenMeteringFilter(usageReporter, tenantQuota));

        if (gw.getSecurity().isOutputValidatorEnabled()) {
            filters.add(new OutputValidator(List.of(new SensitivePatternRule())));
        }

        log.info("Gateway filter chain initialized with {} filters", filters.size());
        return new GatewayFilterChain(filters);
    }
}
