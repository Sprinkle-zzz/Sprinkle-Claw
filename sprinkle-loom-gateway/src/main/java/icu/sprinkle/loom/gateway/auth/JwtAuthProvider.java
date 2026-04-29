package icu.sprinkle.loom.gateway.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JWT 认证提供者。基于 nimbus-jose-jwt，依赖 {@link JWKSource} 解析签名公钥。
 * <p>由调用方决定 JWKSource 的来源（远程 JWKS URL / 本地不可变集 / 自定义），
 * 解耦 HTTP 拉取逻辑，便于测试与运行时按需启用。</p>
 *
 * <p>Claim 约定（可通过构造器自定义）：
 * <ul>
 *   <li>{@code sub} → userId</li>
 *   <li>{@code tenant_id} → tenantId</li>
 *   <li>{@code plan} → plan（默认 FREE）</li>
 *   <li>{@code permissions} → permissions（List&lt;String&gt; 或空格分隔字符串）</li>
 * </ul>
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class JwtAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthProvider.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ConfigurableJWTProcessor<SecurityContext> processor;
    private final String tenantClaim;
    private final String planClaim;
    private final String permissionsClaim;

    public JwtAuthProvider(JWKSource<SecurityContext> jwkSource, JWSAlgorithm algorithm) {
        this(jwkSource, algorithm, null, null, "tenant_id", "plan", "permissions");
    }

    public JwtAuthProvider(JWKSource<SecurityContext> jwkSource,
                           JWSAlgorithm algorithm,
                           String expectedIssuer,
                           String expectedAudience) {
        this(jwkSource, algorithm, expectedIssuer, expectedAudience,
                "tenant_id", "plan", "permissions");
    }

    public JwtAuthProvider(JWKSource<SecurityContext> jwkSource,
                           JWSAlgorithm algorithm,
                           String expectedIssuer,
                           String expectedAudience,
                           String tenantClaim,
                           String planClaim,
                           String permissionsClaim) {
        DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
        p.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithm, jwkSource));

        JWTClaimsSet.Builder requiredBuilder = new JWTClaimsSet.Builder();
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            requiredBuilder.issuer(expectedIssuer);
        }
        if (expectedAudience != null && !expectedAudience.isBlank()) {
            requiredBuilder.audience(expectedAudience);
        }
        Set<String> requiredClaims = new HashSet<>(Set.of("sub", "exp"));
        p.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                requiredBuilder.build(), requiredClaims));

        this.processor = p;
        this.tenantClaim = tenantClaim;
        this.planClaim = planClaim;
        this.permissionsClaim = permissionsClaim;
    }

    @Override
    public Optional<AuthContext> authenticate(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return Optional.empty();
        }
        String token = authHeader.startsWith(BEARER_PREFIX)
                ? authHeader.substring(BEARER_PREFIX.length()).trim()
                : authHeader.trim();
        if (token.isEmpty() || token.indexOf('.') < 0) {
            return Optional.empty();
        }

        try {
            JWTClaimsSet claims = processor.process(token, null);

            String userId = claims.getSubject();
            String tenantId = stringClaim(claims, tenantClaim);
            String plan = stringClaim(claims, planClaim);
            if (plan == null || plan.isBlank()) {
                plan = "FREE";
            }
            Set<String> permissions = parsePermissions(claims.getClaim(permissionsClaim));

            return Optional.of(new AuthContext(userId, tenantId, plan, permissions, "jwt"));
        } catch (ParseException | RuntimeException | com.nimbusds.jose.JOSEException
                 | com.nimbusds.jose.proc.BadJOSEException e) {
            log.debug("[JwtAuthProvider] JWT 校验失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            return null;
        }
    }

    private static Set<String> parsePermissions(Object raw) {
        if (raw == null) {
            return Set.of();
        }
        if (raw instanceof String s) {
            if (s.isBlank()) {
                return Set.of();
            }
            Set<String> out = new LinkedHashSet<>();
            for (String token : s.split("\\s+")) {
                if (!token.isBlank()) {
                    out.add(token);
                }
            }
            return Set.copyOf(out);
        }
        if (raw instanceof Collection<?> c) {
            Set<String> out = new LinkedHashSet<>();
            for (Object o : c) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return Set.copyOf(out);
        }
        if (raw instanceof String[] arr) {
            return Set.copyOf(List.of(arr));
        }
        return Set.of();
    }
}
