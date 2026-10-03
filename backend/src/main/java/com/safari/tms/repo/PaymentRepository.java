package com.safari.tms.repo;

import com.safari.tms.domain.Payment;
import com.safari.tms.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select p from Payment p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              left join fetch p.processedBy
             order by p.createdAt desc
            """)
    List<Payment> findAllDetailed();

    @Query("""
            select p from Payment p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              left join fetch p.processedBy
             where p.id = :id
            """)
    Optional<Payment> findDetailById(@Param("id") Long id);

    @Query("""
            select p from Payment p
              join fetch p.booking b
              join fetch b.customer
              join fetch b.safariPackage sp
              left join fetch p.processedBy
             where b.id = :bookingId
             order by p.createdAt desc
            """)
    List<Payment> findForBooking(@Param("bookingId") Long bookingId);

    /**
     * Sum of settled money against a booking.
     */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
             where p.booking.id = :bookingId
               and p.status = com.safari.tms.domain.enums.PaymentStatus.SUCCESS
            """)
    BigDecimal sumSettledForBooking(@Param("bookingId") Long bookingId);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
             where p.status = com.safari.tms.domain.enums.PaymentStatus.SUCCESS
               and p.paidAt between :from and :to
            """)
    BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatus(PaymentStatus status);
}
