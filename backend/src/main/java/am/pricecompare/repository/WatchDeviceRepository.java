package am.pricecompare.repository;

import am.pricecompare.domain.WatchDevice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchDeviceRepository extends JpaRepository<WatchDevice, String> {
}
