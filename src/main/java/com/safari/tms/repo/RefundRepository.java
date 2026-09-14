package com.safari.tms.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("""
            select r from Refund r
              join fetch r.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              left join fetch r.processedBy
             order by r.requestedAt desc
            """)
    List<Refund> findAllDetailed();

    @Query("""
            select r from Refund r
              join fetch r.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              left join fetch r.processedBy
             where r.id = :id
            """)
    Optional<Refund> findDetailById(@Param("id") Long id);

    @Query("""
            select r from Refund r
              join fetch r.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              left join fetch r.processedBy
             where b.id = :bookingId
             order by r.requestedAt desc
            """)
    List<Refund> findForBooking(@Param("bookingId") Long bookingId);

    boolean existsByBookingIdAndStatusIn(Long bookingId, List<RefundStatus> statuses);

    long countByStatus(RefundStatus status);
}

