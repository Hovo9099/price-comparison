package am.pricecompare.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP caching for public read-only data. Prices change at most once per crawl, so a short public max-age lets the
 * app and any proxy reuse answers; per-user data (shopping lists, watches, devices) and admin are never cached.
 */
@Component
public class CacheControlFilter extends OncePerRequestFilter {

    static String policy(String method, String uri) {
        if (!"GET".equals(method) || !uri.startsWith("/api/")) {
            return null;
        }
        if (uri.startsWith("/api/admin/") || uri.startsWith("/api/shopping-lists") || uri.startsWith("/api/watches")
                || uri.startsWith("/api/devices")) {
            return "no-store";
        }
        if (uri.equals("/api/categories") || uri.startsWith("/api/lexicon/")) {
            return "public, max-age=600";
        }
        if (uri.startsWith("/api/products") || uri.startsWith("/api/deals") || uri.startsWith("/api/stores")) {
            return "public, max-age=60";
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String p = policy(request.getMethod(), request.getRequestURI());
        if (p != null) {
            response.setHeader("Cache-Control", p);
        }
        chain.doFilter(request, response);
    }
}
