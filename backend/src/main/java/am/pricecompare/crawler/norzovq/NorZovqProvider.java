package am.pricecompare.crawler.norzovq;

import am.pricecompare.crawler.CrawlContext;
import am.pricecompare.crawler.DiscoveredCategory;
import am.pricecompare.crawler.PoliteHttpClient;
import am.pricecompare.crawler.SupermarketProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nor Zovq (https://norzovq.am). As of 2026-10-08 the site does not serve content (Cloudflare error 525,
 * SSL handshake failure with the origin), so there is no public catalog to read. {@link #isAvailable()} probes
 * the site so the provider starts working automatically as soon as the site is back; until a parser is written
 * for the restored site, prices can be imported with the CSV fallback.
 */
@Component
public class NorZovqProvider implements SupermarketProvider {

    public static final String CODE = "nor-zovq";
    private final PoliteHttpClient http;
    private volatile String lastReason =
            "No publicly accessible catalog: norzovq.am returned Cloudflare error 525 on 2026-10-08 (re-checked on each crawl request). Use CSV import.";

    public NorZovqProvider(PoliteHttpClient http) {
        this.http = http;
    }

    @Override
    public String getStoreCode() {
        return CODE;
    }

    @Override
    public String getStoreName() {
        return "Nor Zovq";
    }

    @Override
    public boolean isImplemented() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        try {
            http.get("https://norzovq.am/");
            lastReason = "Site responds again, but no catalog parser has been written for it yet; use CSV import";
        } catch (RuntimeException e) {
            lastReason = "No publicly accessible catalog: " + e.getMessage() + " (Cloudflare 525 since at least 2026-10-08). Use CSV import.";
        }
        return false;
    }

    @Override
    public String unavailableReason() {
        return lastReason;
    }

    @Override
    public List<DiscoveredCategory> discoverCategories() {
        return List.of();
    }

    @Override
    public void discoverProducts(CrawlContext ctx) {
        throw new UnsupportedOperationException(unavailableReason());
    }
}
