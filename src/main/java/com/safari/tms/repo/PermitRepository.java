package com.safari.tms.repo;

import com.safari.tms.domain.Permit;
import com.safari.tms.domain.enums.PermitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PermitRepository extends JpaRepository<Permit, Long> {

    @Query("""
            select p from Permit p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              join fetch p.park
             order by p.expiryDate asc
            """)
    List<Permit> findAllDetailed();

    @Query("""
            select p from Permit p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              join fetch p.park
             where p.id = :id
            """)
    Optional<Permit> findDetailById(@Param("id") Long id);

    @Query("""
            select p from Permit p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              join fetch p.park
             where b.id = :bookingId
             order by p.createdAt desc
            """)
    List<Permit> findForBooking(@Param("bookingId") Long bookingId);

    /** Approved permits that lapse on or before the given cut-off date. */
    @Query("""
            select p from Permit p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              join fetch p.park
             where p.status = com.safari.tms.domain.enums.PermitStatus.APPROVED
               and p.expiryDate <= :cutoff
             order by p.expiryDate asc
            """)
    List<Permit> findExpiringOnOrBefore(@Param("cutoff") LocalDate cutoff);

    long countByStatus(PermitStatus status);
}
