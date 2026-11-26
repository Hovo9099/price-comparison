package am.pricecompare.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client token bucket for the public API ({@code /api/**} except admin): {@code app.rate-limit.per-minute}
 * requests per minute with bursts up to that size; 0 disables it. Over the limit → 429 with Retry-After.
 * Behind a reverse proxy set {@code server.forward-headers-strategy=native} so the client IP (not the proxy's) is used.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int perMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.per-minute:240}") int perMinute) {
        this.perMinute = perMinute;
    }

    static final class Bucket {
        double tokens;
        long lastNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastNanos = now;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return perMinute <= 0 || !uri.startsWith("/api/") || uri.startsWith("/api/admin/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long waitSeconds = acquire(request.getRemoteAddr(), System.nanoTime());
        if (waitSeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Rate limit of "
                    + perMinute + " requests per minute exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** @return 0 when the request may proceed, otherwise the seconds to wait */
    long acquire(String client, long now) {
        double perNano = perMinute / 60e9;
        Bucket b = buckets.computeIfAbsent(client, k -> new Bucket(perMinute, now));
        synchronized (b) {
            b.tokens = Math.min(perMinute, b.tokens + (now - b.lastNanos) * perNano);
            b.lastNanos = now;
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - b.tokens) / perNano / 1e9));
        }
    }

    /** Forget clients idle for 10 minutes (their bucket would be full anyway). */
    @Scheduled(fixedDelay = 600_000)
    void evictIdle() {
        long cutoff = System.nanoTime() - 600_000_000_000L;
        buckets.entrySet().removeIf(e -> e.getValue().lastNanos < cutoff);
    }

    int trackedClients() {
        return buckets.size();
    }
}
