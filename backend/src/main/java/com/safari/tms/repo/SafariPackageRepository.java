package com.safari.tms.repo;

import com.safari.tms.domain.SafariPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SafariPackageRepository extends JpaRepository<SafariPackage, Long> {

    @Query("""
            select p from SafariPackage p
              join fetch p.park pk
             where (:activeOnly = false or p.active = true)
               and (:parkId is null or pk.id = :parkId)
               and (:minPrice is null or p.pricePerPerson >= :minPrice)
               and (:maxPrice is null or p.pricePerPerson <= :maxPrice)
               and (:minDays is null or p.durationDays >= :minDays)
               and (:maxDays is null or p.durationDays <= :maxDays)
               and (:term is null
                    or lower(p.name) like :term
                    or lower(p.description) like :term
                    or lower(pk.name) like :term
                    or lower(pk.location) like :term)
             order by p.name asc
            """)
    List<SafariPackage> search(@Param("activeOnly") boolean activeOnly,
                               @Param("parkId") Long parkId,
                               @Param("minPrice") BigDecimal minPrice,
                               @Param("maxPrice") BigDecimal maxPrice,
                               @Param("minDays") Integer minDays,
                               @Param("maxDays") Integer maxDays,
                               @Param("term") String term);

    @Query("select p from SafariPackage p join fetch p.park where p.id = :id")
    SafariPackage findDetailById(@Param("id") Long id);

    @Query("select p from SafariPackage p join fetch p.park order by p.name asc")
    List<SafariPackage> findAllWithPark();

    /**
     * Takes a write lock on the package row. Booking creation calls this first so two concurrent
     * requests for the same departure cannot both pass the capacity check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SafariPackage p where p.id = :id")
    Optional<SafariPackage> findByIdForUpdate(@Param("id") Long id);

    long countByParkId(Long parkId);
}
