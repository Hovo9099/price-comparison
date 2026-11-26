package am.pricecompare.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * HTTP client used by all providers. It identifies itself with a descriptive User-Agent, honours robots.txt,
 * enforces a minimum delay between requests to the same host, and retries temporary failures
 * (timeouts, connection errors, HTTP 429/5xx) with exponential backoff.
 */
@Component
public class PoliteHttpClient {

    private static final Logger log = LoggerFactory.getLogger(PoliteHttpClient.class);

    private final CrawlerProperties props;
    private final HttpClient client;
    private final Map<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequestAt = new ConcurrentHashMap<>();
    private final Map<String, Long> hostDelayOverride = new ConcurrentHashMap<>();
    private final Map<String, RobotsRules> robotsCache = new ConcurrentHashMap<>();

    public PoliteHttpClient(CrawlerProperties props) {
        this.props = props;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static class FetchException extends RuntimeException {
        private final int status;

        public FetchException(String message, int status, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public static class RobotsDisallowedException extends FetchException {
        public RobotsDisallowedException(String url) {
            super("Disallowed by robots.txt: " + url, 0, null);
        }
    }

    /** Overrides the minimum delay for one host (e.g. a slower source). */
    public void setHostDelay(String host, long delayMs) {
        hostDelayOverride.put(host, delayMs);
    }

    public String get(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        return execute(url, b, headers);
    }

    public String get(String url) {
        return get(url, Map.of());
    }

    public String postJson(String url, String jsonBody, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json");
        return execute(url, b, headers);
    }

    private String execute(String url, HttpRequest.Builder builder, Map<String, String> headers) {
        URI uri = URI.create(url);
        checkRobots(uri);
        builder.timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .header("User-Agent", props.getUserAgent())
                .header("Accept-Encoding", "gzip");
        headers.forEach(builder::header);
        HttpRequest request = builder.build();

        int attempts = props.getMaxRetries() + 1;
        FetchException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            throttle(uri.getHost());
            try {
                HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = resp.statusCode();
                String body = readBody(resp);
                if (status >= 200 && status < 300) {
                    return body;
                }
                if (status == 429 || status >= 500) {
                    long wait = retryAfterMs(resp).orElse(backoffMs(attempt));
                    last = new FetchException("HTTP " + status + " for " + url, status, null);
                    log.warn("Temporary HTTP {} for {} (attempt {}/{}), waiting {} ms", status, url, attempt, attempts, wait);
                    sleep(wait);
                    continue;
                }
                throw new FetchException("HTTP " + status + " for " + url, status, null);
            } catch (HttpTimeoutException e) {
                last = new FetchException("Timeout for " + url, 0, e);
                log.warn("Timeout for {} (attempt {}/{})", url, attempt, attempts);
                sleep(backoffMs(attempt));
            } catch (IOException e) {
                last = new FetchException("I/O error for " + url + ": " + e.getMessage(), 0, e);
                log.warn("I/O error for {} (attempt {}/{}): {}", url, attempt, attempts, e.toString());
                sleep(backoffMs(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("Interrupted while fetching " + url, 0, e);
            }
        }
        throw last != null ? last : new FetchException("Failed to fetch " + url, 0, null);
    }

    private static String readBody(HttpResponse<InputStream> resp) throws IOException {
        try (InputStream raw = resp.body()) {
            byte[] bytes = raw.readAllBytes();
            boolean gzip = resp.headers().firstValue("Content-Encoding").map(v -> v.toLowerCase().contains("gzip")).orElse(false);
            if (gzip) {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    bytes = in.readAllBytes();
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private java.util.Optional<Long> retryAfterMs(HttpResponse<?> resp) {
        return resp.headers().firstValue("Retry-After").flatMap(v -> {
            try {
                return java.util.Optional.of(Math.min(120_000L, Long.parseLong(v.trim()) * 1000));
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
        });
    }

    private static long backoffMs(int attempt) {
        return Math.min(30_000L, 1000L * (1L << (attempt - 1)));
    }

    private void throttle(String host) {
        long delay = hostDelayOverride.getOrDefault(host, props.getMinDelayMs());
        Object lock = hostLocks.computeIfAbsent(host, h -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long last = lastRequestAt.getOrDefault(host, 0L);
            long wait = last + delay - now;
            if (wait > 0) {
                sleep(wait);
            }
            lastRequestAt.put(host, System.currentTimeMillis());
        }
    }

    private void checkRobots(URI uri) {
        if (!props.isRespectRobotsTxt() || uri.getPath().equals("/robots.txt")) {
            return;
        }
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        RobotsRules rules = robotsCache.computeIfAbsent(origin, this::loadRobots);
        String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        if (!rules.isAllowed(pathAndQuery)) {
            throw new RobotsDisallowedException(uri.toString());
        }
    }

    private RobotsRules loadRobots(String origin) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .header("User-Agent", props.getUserAgent())
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            String ct = resp.headers().firstValue("Content-Type").orElse("");
            if (resp.statusCode() == 200 && !ct.contains("html")) {
                log.info("Loaded robots.txt for {}", origin);
                return RobotsRules.parse(resp.body());
            }
            log.info("No robots.txt for {} (HTTP {}, {}), assuming allow-all", origin, resp.statusCode(), ct);
        } catch (IOException e) {
            log.warn("Could not load robots.txt for {}: {}", origin, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return RobotsRules.allowAll();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted", 0, e);
        }
    }
}
