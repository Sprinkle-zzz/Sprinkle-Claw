package icu.sprinkle.loom.gateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthProviderTest {

    private static RSAKey rsaKey;
    private static JWKSource<SecurityContext> jwkSource;

    @BeforeAll
    static void generateKey() throws JOSEException {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey.toPublicJWK()));
    }

    private static String sign(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    void validJwtProducesAuthContext() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .claim("tenant_id", "tenant-x")
                .claim("plan", "PRO")
                .claim("permissions", List.of("read", "write"))
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        Optional<AuthContext> ctx = provider.authenticate("Bearer " + sign(claims));

        assertThat(ctx).isPresent();
        AuthContext c = ctx.get();
        assertThat(c.userId()).isEqualTo("user-1");
        assertThat(c.tenantId()).isEqualTo("tenant-x");
        assertThat(c.plan()).isEqualTo("PRO");
        assertThat(c.permissions()).containsExactlyInAnyOrder("read", "write");
        assertThat(c.authMethod()).isEqualTo("jwt");
    }

    @Test
    void missingPlanDefaultsToFree() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-2")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        Optional<AuthContext> ctx = provider.authenticate("Bearer " + sign(claims));

        assertThat(ctx).isPresent();
        assertThat(ctx.get().plan()).isEqualTo("FREE");
    }

    @Test
    void permissionsAsSpaceSeparatedStringIsParsed() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-3")
                .claim("permissions", "read write admin")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        Optional<AuthContext> ctx = provider.authenticate("Bearer " + sign(claims));

        assertThat(ctx).isPresent();
        assertThat(ctx.get().permissions()).containsExactlyInAnyOrder("read", "write", "admin");
    }

    @Test
    void expiredJwtIsRejected() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-4")
                .expirationTime(new Date(System.currentTimeMillis() - 60_000))
                .build();

        Optional<AuthContext> ctx = provider.authenticate("Bearer " + sign(claims));

        assertThat(ctx).isEmpty();
    }

    @Test
    void wrongIssuerIsRejected() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(
                jwkSource, JWSAlgorithm.RS256, "https://expected.example.com", null);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-5")
                .issuer("https://other.example.com")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        Optional<AuthContext> ctx = provider.authenticate("Bearer " + sign(claims));

        assertThat(ctx).isEmpty();
    }

    @Test
    void tamperedSignatureIsRejected() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-6")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        String token = sign(claims);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        Optional<AuthContext> ctx = provider.authenticate("Bearer " + tampered);

        assertThat(ctx).isEmpty();
    }

    @Test
    void blankOrMalformedHeaderReturnsEmpty() {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        assertThat(provider.authenticate(null)).isEmpty();
        assertThat(provider.authenticate("")).isEmpty();
        assertThat(provider.authenticate("Bearer ")).isEmpty();
        assertThat(provider.authenticate("Bearer not-a-jwt")).isEmpty();
    }

    @Test
    void rawTokenWithoutBearerPrefixWorks() throws JOSEException {
        JwtAuthProvider provider = new JwtAuthProvider(jwkSource, JWSAlgorithm.RS256);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-7")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        Optional<AuthContext> ctx = provider.authenticate(sign(claims));

        assertThat(ctx).isPresent();
        assertThat(ctx.get().userId()).isEqualTo("user-7");
    }
}
