package icu.sprinkle.loom.gateway.auth;

import icu.sprinkle.loom.gateway.ErrorCode;
import icu.sprinkle.loom.gateway.GatewayRequest;
import icu.sprinkle.loom.gateway.filter.FilterResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthFilterTest {

    @Test
    void rejectsWhenAuthHeaderMissing() {
        AuthFilter filter = new AuthFilter(new ApiKeyAuthProvider(new InMemoryApiKeyStore()));
        FilterResult result = filter.preFilter(GatewayRequest.of(null, "ip", "msg"));
        assertInstanceOf(FilterResult.Reject.class, result);
        assertEquals(ErrorCode.AUTH_001, ((FilterResult.Reject) result).errorCode());
    }

    @Test
    void rejectsWhenKeyNotInStore() {
        AuthFilter filter = new AuthFilter(new ApiKeyAuthProvider(new InMemoryApiKeyStore()));
        FilterResult result = filter.preFilter(GatewayRequest.of("sk-unknown", "ip", "msg"));
        assertInstanceOf(FilterResult.Reject.class, result);
        assertEquals(ErrorCode.AUTH_002, ((FilterResult.Reject) result).errorCode());
    }

    @Test
    void passesAndWritesAuthContextForValidBearerKey() {
        InMemoryApiKeyStore store = new InMemoryApiKeyStore();
        store.register(new ApiKeyStore.ApiKeyEntry("sk-abc", "tenant-a", "user-1", "PRO"));
        AuthFilter filter = new AuthFilter(new ApiKeyAuthProvider(store));

        GatewayRequest request = GatewayRequest.of("Bearer sk-abc", "ip", "msg");
        FilterResult result = filter.preFilter(request);

        assertInstanceOf(FilterResult.Pass.class, result);
        assertTrue(request.authContext().isPresent());
        assertEquals("tenant-a", request.authContext().get().tenantId());
        assertEquals("user-1", request.authContext().get().userId());
    }

    @Test
    void passesWithDirectKeyFormat() {
        InMemoryApiKeyStore store = new InMemoryApiKeyStore();
        store.register(new ApiKeyStore.ApiKeyEntry("sk-abc", "tenant-a", "user-1", "FREE"));
        AuthFilter filter = new AuthFilter(new ApiKeyAuthProvider(store));

        FilterResult result = filter.preFilter(GatewayRequest.of("sk-abc", "ip", "msg"));
        assertInstanceOf(FilterResult.Pass.class, result);
    }
}
