package am.pricecompare.repository;

import am.pricecompare.domain.PriceWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PriceWatchRepository extends JpaRepository<PriceWatch, Long> {

    @Query("select w from PriceWatch w join fetch w.canonicalProduct where w.device.deviceId = :deviceId order by w.createdAt desc")
    List<PriceWatch> findByDevice(String deviceId);

    Optional<PriceWatch> findByDeviceDeviceIdAndCanonicalProductId(String deviceId, long canonicalProductId);

    long countByDeviceDeviceId(String deviceId);

    @Query("select w from PriceWatch w join fetch w.device join fetch w.canonicalProduct where w.canonicalProduct.active = true")
    List<PriceWatch> findAllActive();
}
