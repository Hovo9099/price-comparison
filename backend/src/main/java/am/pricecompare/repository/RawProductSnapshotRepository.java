package am.pricecompare.repository;

import am.pricecompare.domain.RawProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawProductSnapshotRepository extends JpaRepository<RawProductSnapshot, Long> {
}
