package am.pricecompare.crawler;

import am.pricecompare.domain.Store;
import am.pricecompare.domain.StoreLocation;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Upserts a store's published branches into store_locations; branches no longer listed become inactive. */
@Service
public class LocationSyncService {

    private final EntityManager em;

    public LocationSyncService(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public int sync(long storeId, List<DiscoveredLocation> locations) {
        if (locations.isEmpty()) {
            return 0;
        }
        Store store = em.getReference(Store.class, storeId);
        Map<String, StoreLocation> existing = new HashMap<>();
        em.createQuery("select l from StoreLocation l where l.store.id = :id", StoreLocation.class)
                .setParameter("id", storeId).getResultList().forEach(l -> existing.put(l.getName(), l));
        Set<String> seen = new HashSet<>();
        for (DiscoveredLocation d : locations) {
            String name = d.name().length() > 300 ? d.name().substring(0, 300) : d.name();
            if (!seen.add(name)) {
                continue;
            }
            StoreLocation l = existing.get(name);
            if (l == null) {
                l = new StoreLocation();
                l.setStore(store);
                l.setName(name);
                em.persist(l);
            }
            l.setAddress(d.address() == null ? null : d.address().length() > 500 ? d.address().substring(0, 500) : d.address());
            l.setLatitude(scale(d.latitude()));
            l.setLongitude(scale(d.longitude()));
            l.setActive(true);
        }
        existing.values().stream().filter(l -> !seen.contains(l.getName())).forEach(l -> l.setActive(false));
        return seen.size();
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(6, RoundingMode.HALF_UP);
    }
}
