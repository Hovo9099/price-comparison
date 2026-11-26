package am.pricecompare.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    /** Maximum number of products per run. 0 = unlimited (full catalog import). */
    private int maxProducts = 0;
    /** Identifying user agent sent with every request. */
    private String userAgent = "WhereIsItCheapestBot/0.1 (price comparison research; +https://github.com/where-is-it-cheapest)";
    private int connectTimeoutMs = 15000;
    private int readTimeoutMs = 45000;
    private int maxRetries = 3;
    /** Minimum delay between two requests to the same host. */
    private long minDelayMs = 700;
    private boolean respectRobotsTxt = true;
    /** Keep the raw payload of every observed product (raw_product_snapshots). */
    private boolean storeRawSnapshots = true;
    /** Mark products not seen in a complete, successful, unlimited run as unavailable. */
    private boolean deactivateMissing = true;
    /** Optional cron expression for scheduled crawls of all available stores, empty = disabled. */
    private String scheduleCron = "";
    private Map<String, ProviderSettings> providers = new HashMap<>();

    public ProviderSettings provider(String code) {
        return providers.getOrDefault(code, new ProviderSettings());
    }

    public static class ProviderSettings {
        private boolean enabled = true;
        /** NONE, MISSING (only products never enriched) or ALL. */
        private String detailPages = "MISSING";
        private boolean fetchEnglishNames = true;
        private Long minDelayMs;
        private Map<String, String> options = new HashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDetailPages() { return detailPages; }
        public void setDetailPages(String detailPages) { this.detailPages = detailPages; }
        public boolean isFetchEnglishNames() { return fetchEnglishNames; }
        public void setFetchEnglishNames(boolean fetchEnglishNames) { this.fetchEnglishNames = fetchEnglishNames; }
        public Long getMinDelayMs() { return minDelayMs; }
        public void setMinDelayMs(Long minDelayMs) { this.minDelayMs = minDelayMs; }
        public Map<String, String> getOptions() { return options; }
        public void setOptions(Map<String, String> options) { this.options = options; }

        public boolean detailsFor(String externalId, java.util.Set<String> alreadyFetched) {
            return switch (detailPages.toUpperCase()) {
                case "ALL" -> true;
                case "NONE" -> false;
                default -> !alreadyFetched.contains(externalId);
            };
        }
    }

    public int getMaxProducts() { return maxProducts; }
    public void setMaxProducts(int maxProducts) { this.maxProducts = maxProducts; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getMinDelayMs() { return minDelayMs; }
    public void setMinDelayMs(long minDelayMs) { this.minDelayMs = minDelayMs; }
    public boolean isRespectRobotsTxt() { return respectRobotsTxt; }
    public void setRespectRobotsTxt(boolean respectRobotsTxt) { this.respectRobotsTxt = respectRobotsTxt; }
    public boolean isStoreRawSnapshots() { return storeRawSnapshots; }
    public void setStoreRawSnapshots(boolean storeRawSnapshots) { this.storeRawSnapshots = storeRawSnapshots; }
    public boolean isDeactivateMissing() { return deactivateMissing; }
    public void setDeactivateMissing(boolean deactivateMissing) { this.deactivateMissing = deactivateMissing; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public Map<String, ProviderSettings> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderSettings> providers) { this.providers = providers; }
}
