package am.pricecompare.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAndCacheTest {

    @Test
    void bucketAllowsBurstThenRefillsOverTime() {
        RateLimitFilter f = new RateLimitFilter(60); // 1 per second, burst 60
        long t = 1_000_000_000L;
        for (int i = 0; i < 60; i++) {
            assertThat(f.acquire("1.2.3.4", t)).isZero();
        }
        assertThat(f.acquire("1.2.3.4", t)).isEqualTo(1);                 // empty: wait ~1 s
        assertThat(f.acquire("5.6.7.8", t)).isZero();                     // other clients unaffected
        assertThat(f.acquire("1.2.3.4", t + 1_000_000_000L)).isZero();    // one token after a second
        assertThat(f.acquire("1.2.3.4", t + 1_000_000_000L)).isPositive();
    }

    @Test
    void returns429WithRetryAfterAndSkipsAdmin() throws Exception {
        RateLimitFilter f = new RateLimitFilter(2);
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            f.doFilter(req("/api/products/search"), ok, new MockFilterChain());
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse limited = new MockHttpServletResponse();
        f.doFilter(req("/api/products/search"), limited, new MockFilterChain());
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();

        MockHttpServletResponse admin = new MockHttpServletResponse();
        f.doFilter(req("/api/admin/dashboard"), admin, new MockFilterChain());
        assertThat(admin.getStatus()).isEqualTo(200);
        MockHttpServletResponse health = new MockHttpServletResponse();
        f.doFilter(req("/actuator/health"), health, new MockFilterChain());
        assertThat(health.getStatus()).isEqualTo(200);
    }

    @Test
    void disabledWithZero() throws Exception {
        RateLimitFilter f = new RateLimitFilter(0);
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse r = new MockHttpServletResponse();
            f.doFilter(req("/api/products/search"), r, new MockFilterChain());
            assertThat(r.getStatus()).isEqualTo(200);
        }
        assertThat(f.trackedClients()).isZero();
    }

    @Test
    void cachePolicies() {
        assertThat(CacheControlFilter.policy("GET", "/api/products/12/prices")).isEqualTo("public, max-age=60");
        assertThat(CacheControlFilter.policy("GET", "/api/categories")).isEqualTo("public, max-age=600");
        assertThat(CacheControlFilter.policy("GET", "/api/watches")).isEqualTo("no-store");
        assertThat(CacheControlFilter.policy("GET", "/api/shopping-lists/5")).isEqualTo("no-store");
        assertThat(CacheControlFilter.policy("GET", "/api/admin/dashboard")).isEqualTo("no-store");
        assertThat(CacheControlFilter.policy("POST", "/api/shopping-lists")).isNull();
        assertThat(CacheControlFilter.policy("GET", "/actuator/health")).isNull();
    }

    private static MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRemoteAddr("203.0.113.9");
        return r;
    }
}
