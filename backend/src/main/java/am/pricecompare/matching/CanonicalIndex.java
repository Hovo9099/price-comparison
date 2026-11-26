package am.pricecompare.matching;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of canonical products' parsed features, bucketed by (sold-by-weight, unit, size) so that
 * matching an incoming product only scores plausible candidates. Not thread-safe on its own; guarded by
 * {@link MatchingService}.
 */
final class CanonicalIndex {

    static final class Entry {
        final long id;
        volatile ProductFeatures features;
        final Set<Long> storeIds = ConcurrentHashMap.newKeySet();

        Entry(long id, ProductFeatures features) {
            this.id = id;
            this.features = features;
        }
    }

    private final Map<Long, Entry> byId = new HashMap<>();
    private final Map<String, List<Entry>> buckets = new HashMap<>();
    private final Map<String, Long> byBarcode = new HashMap<>();
    /** Number of indexed products whose names contain each token key (for IDF weights). */
    private final Map<String, Integer> documentFrequency = new HashMap<>();

    static String bucketKey(ProductFeatures f) {
        String size = f.sizeValue() == null || f.sizeUnit() == null ? "none"
                : f.sizeUnit() + ":" + f.sizeValue().stripTrailingZeros().toPlainString();
        return (f.soldByWeight() ? "w|" : "u|") + size;
    }

    int size() {
        return byId.size();
    }

    Entry get(long id) {
        return byId.get(id);
    }

    void put(long id, ProductFeatures f) {
        Entry existing = byId.get(id);
        if (existing != null) {
            remove(id);
        }
        Entry e = new Entry(id, f);
        if (existing != null) {
            e.storeIds.addAll(existing.storeIds);
        }
        byId.put(id, e);
        keys(f).forEach(k -> documentFrequency.merge(k, 1, Integer::sum));
        buckets.computeIfAbsent(bucketKey(f), k -> new ArrayList<>()).add(e);
        if (f.barcode() != null) {
            byBarcode.putIfAbsent(f.barcode(), id);
        }
    }

    void remove(long id) {
        Entry e = byId.remove(id);
        if (e == null) {
            return;
        }
        keys(e.features).forEach(k -> documentFrequency.computeIfPresent(k, (key, n) -> n <= 1 ? null : n - 1));
        List<Entry> list = buckets.get(bucketKey(e.features));
        if (list != null) {
            list.remove(e);
        }
        if (e.features.barcode() != null) {
            byBarcode.remove(e.features.barcode(), id);
        }
    }

    void addStore(long canonicalId, long storeId) {
        Entry e = byId.get(canonicalId);
        if (e != null) {
            e.storeIds.add(storeId);
        }
    }

    void removeStore(long canonicalId, long storeId) {
        Entry e = byId.get(canonicalId);
        if (e != null) {
            e.storeIds.remove(storeId);
        }
    }

    /** Inverse document frequency weight: generic tokens ("wine", "milk") weigh less than distinctive ones. */
    ProductMatcher.TokenWeights weights() {
        int n = Math.max(1, byId.size());
        return key -> {
            int df = documentFrequency.getOrDefault(key, 0);
            return 1.0 + Math.log((n + 1.0) / (df + 1.0));
        };
    }

    Long byBarcode(String barcode) {
        return barcode == null ? null : byBarcode.get(barcode);
    }

    /** Entries in the same size bucket sharing at least one name token (or the brand) with {@code f}. */
    List<Entry> candidates(ProductFeatures f) {
        List<Entry> bucket = buckets.get(bucketKey(f));
        if (bucket == null || bucket.isEmpty()) {
            return List.of();
        }
        Set<String> keys = keys(f);
        List<Entry> out = new ArrayList<>();
        for (Entry e : bucket) {
            ProductFeatures o = e.features;
            if (f.brandKey() != null && f.brandKey().equals(o.brandKey())) {
                out.add(e);
                continue;
            }
            for (String k : keys(o)) {
                if (keys.contains(k)) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    private static Set<String> keys(ProductFeatures f) {
        Set<String> s = new HashSet<>(f.name().keys());
        if (f.nameEn() != null) {
            s.addAll(f.nameEn().keys());
        }
        s.removeIf(k -> k.length() < 3 && !k.chars().allMatch(Character::isDigit));
        return s;
    }

    static boolean sameSize(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
