package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.gateway.acl.AccessControlList;
import icu.sprinkle.loom.gateway.acl.AclFilter;
import icu.sprinkle.loom.gateway.audit.AsyncBufferedAuditLogger;
import icu.sprinkle.loom.gateway.audit.AuditFilter;
import icu.sprinkle.loom.gateway.audit.AuditLogger;
import icu.sprinkle.loom.gateway.auth.ApiKeyAuthProvider;
import icu.sprinkle.loom.gateway.auth.ApiKeyStore;
import icu.sprinkle.loom.gateway.auth.AuthFilter;
import icu.sprinkle.loom.gateway.auth.AuthProvider;
import icu.sprinkle.loom.gateway.auth.InMemoryApiKeyStore;
import icu.sprinkle.loom.gateway.filter.GatewayFilter;
import icu.sprinkle.loom.gateway.filter.GatewayFilterChain;
import icu.sprinkle.loom.gateway.metrics.AsyncBufferedUsageReporter;
import icu.sprinkle.loom.gateway.metrics.TokenMeteringFilter;
import icu.sprinkle.loom.gateway.metrics.UsageReporter;
import icu.sprinkle.loom.gateway.ratelimit.Bucket4jRateLimiter;
import icu.sprinkle.loom.gateway.ratelimit.RateLimitFilter;
import icu.sprinkle.loom.gateway.ratelimit.RateLimiter;
import icu.sprinkle.loom.gateway.security.KeywordInjectionDetector;
import icu.sprinkle.loom.gateway.security.OutputValidator;
import icu.sprinkle.loom.gateway.security.PromptInjectionGuard;
import icu.sprinkle.loom.gateway.security.SensitivePatternRule;
import icu.sprinkle.loom.gateway.tenant.TenantFilter;
import icu.sprinkle.loom.gateway.tenant.TenantQuota;
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
 * Gateway 过滤器链自动配置。仅当 {@code sprinkle-loom.gateway.enabled=true} 时生效。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
@AutoConfiguration(after = SprinkleLoomAutoConfiguration.class)
@ConditionalOnProperty(prefix = "sprinkle-loom.gateway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SprinkleLoomProperties.class)
public class GatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyStore apiKeyStore(SprinkleLoomProperties properties) {
        InMemoryApiKeyStore store = new InMemoryApiKeyStore();
        for (SprinkleLoomProperties.Auth.Key k : properties.getGateway().getAuth().getKeys()) {
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
    public AccessControlList accessControlList(SprinkleLoomProperties properties) {
        SprinkleLoomProperties.Acl aclCfg = properties.getGateway().getAcl();
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
    @ConditionalOnProperty(prefix = "sprinkle-loom.gateway.audit", name = "enabled", havingValue = "true")
    public AuditLogger auditLogger() {
        return new AsyncBufferedAuditLogger(event ->
                log.info("Audit: {}", event));
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayFilterChain gatewayFilterChain(
            SprinkleLoomProperties properties,
            AccessControlList acl,
            List<AuthProvider> authProviders,
            RateLimiter rateLimiter,
            TenantQuota tenantQuota,
            UsageReporter usageReporter,
            ObjectProvider<AuditLogger> auditLoggerProvider) {

        SprinkleLoomProperties.Gateway gw = properties.getGateway();
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
