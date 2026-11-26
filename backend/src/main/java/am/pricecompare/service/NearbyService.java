package am.pricecompare.service;

import am.pricecompare.domain.StoreLocation;
import am.pricecompare.web.dto.NearbyStoreDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class NearbyService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final EntityManager em;

    public NearbyService(EntityManager em) {
        this.em = em;
    }

    /** Great-circle distance in km. */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Nearest active branch of every store that has located branches, closest store first. */
    public List<NearbyStoreDto> nearestPerStore(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
        List<StoreLocation> all = em.createQuery("select l from StoreLocation l join fetch l.store s where l.active = true "
                + "and s.active = true and l.latitude is not null and l.longitude is not null", StoreLocation.class).getResultList();
        Map<Long, NearbyStoreDto> best = new LinkedHashMap<>();
        for (StoreLocation l : all) {
            double d = distanceKm(lat, lon, l.getLatitude().doubleValue(), l.getLongitude().doubleValue());
            NearbyStoreDto cur = best.get(l.getStore().getId());
            if (cur == null || d < cur.distanceKm()) {
                best.put(l.getStore().getId(), new NearbyStoreDto(l.getStore().getId(), l.getStore().getName(), l.getStore().getSlug(),
                        l.getId(), l.getName(), l.getAddress(), l.getLatitude(), l.getLongitude(), Math.round(d * 100) / 100.0));
            }
        }
        return best.values().stream().sorted(Comparator.comparingDouble(NearbyStoreDto::distanceKm)).toList();
    }
}
