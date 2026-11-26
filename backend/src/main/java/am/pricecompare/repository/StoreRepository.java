package am.pricecompare.repository;

import am.pricecompare.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findBySlug(String slug);

    List<Store> findAllByOrderByIdAsc();
}
