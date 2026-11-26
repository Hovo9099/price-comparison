package am.pricecompare.repository;

import am.pricecompare.domain.CanonicalProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CanonicalProductRepository extends JpaRepository<CanonicalProduct, Long> {

    List<CanonicalProduct> findByBarcodeAndActiveTrue(String barcode);

    @Query("select c from CanonicalProduct c where lower(c.name) like lower(concat('%', :q, '%')) "
            + "or lower(c.nameEn) like lower(concat('%', :q, '%')) or c.barcode = :q")
    Page<CanonicalProduct> adminSearch(@Param("q") String q, Pageable pageable);
}
