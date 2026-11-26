package am.pricecompare.repository;

import am.pricecompare.domain.StoreProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    Optional<StoreProduct> findByStoreIdAndExternalProductId(Long storeId, String externalProductId);

    @Query("select sp from StoreProduct sp join fetch sp.store where sp.canonicalProduct.id = :canonicalId")
    List<StoreProduct> findByCanonicalProductIdWithStore(@Param("canonicalId") Long canonicalId);

    @Query("select sp from StoreProduct sp join fetch sp.store where sp.canonicalProduct.id in :ids")
    List<StoreProduct> findByCanonicalProductIdIn(@Param("ids") Collection<Long> ids);

    List<StoreProduct> findByBarcodeAndCanonicalProductIsNotNull(String barcode);

    @Query("select sp.externalProductId from StoreProduct sp where sp.store.id = :storeId and sp.detailsFetchedAt is not null")
    List<String> findExternalIdsWithDetails(@Param("storeId") Long storeId);

    @Query("select sp from StoreProduct sp join fetch sp.store s where (:storeId is null or s.id = :storeId) "
            + "and (:unmatchedOnly = false or sp.canonicalProduct is null) "
            + "and (:q is null or lower(sp.externalName) like lower(concat('%', cast(:q as string), '%')) "
            + "     or lower(sp.externalNameEn) like lower(concat('%', cast(:q as string), '%')))")
    Page<StoreProduct> adminSearch(@Param("storeId") Long storeId, @Param("unmatchedOnly") boolean unmatchedOnly,
                                   @Param("q") String q, Pageable pageable);

    long countByStoreIdAndActiveTrue(Long storeId);

    long countByStoreId(Long storeId);

    long countByCanonicalProductIsNull();

    /** Products of a store not seen since the given instant: they disappeared from the catalog. */
    @Modifying
    @Query("update StoreProduct sp set sp.active = false, sp.updatedAt = :now "
            + "where sp.store.id = :storeId and sp.active = true and (sp.lastSeenAt is null or sp.lastSeenAt < :since)")
    int deactivateNotSeenSince(@Param("storeId") Long storeId, @Param("since") Instant since, @Param("now") Instant now);

    @Query("select sp.id from StoreProduct sp where sp.store.id = :storeId and sp.active = true "
            + "and (sp.lastSeenAt is null or sp.lastSeenAt < :since)")
    List<Long> findIdsNotSeenSince(@Param("storeId") Long storeId, @Param("since") Instant since);

    @Query("select sp.id from StoreProduct sp where sp.canonicalProduct is null order by sp.id")
    List<Long> findUnmatchedIds();
}
