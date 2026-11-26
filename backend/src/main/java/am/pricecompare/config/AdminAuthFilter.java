package am.pricecompare.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects {@code /api/admin/**}. When {@code app.admin.token} (env ADMIN_TOKEN) is set, requests must send it in
 * the {@code X-Admin-Token} header. When it is not set, admin endpoints only accept requests from the loopback
 * interface (local development).
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";
    private final byte[] token;

    public AdminAuthFilter(@Value("${app.admin.token:}") String token) {
        this.token = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin") || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean allowed;
        if (token != null) {
            String provided = request.getHeader(HEADER);
            allowed = provided != null && MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8));
        } else {
            allowed = isLoopback(request.getRemoteAddr());
        }
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Missing or invalid " + HEADER + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLoopback(String addr) {
        try {
            return addr != null && InetAddress.getByName(addr).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }
}
