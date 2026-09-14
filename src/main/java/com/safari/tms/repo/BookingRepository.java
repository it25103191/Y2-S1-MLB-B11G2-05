package com.safari.tms.repo;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    @Query("""
            select b from Booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
             where b.customer.id = :customerId
             order by b.createdAt desc
            """)
    List<Booking> findForCustomer(@Param("customerId") Long customerId);

    @Query("""
            select b from Booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
             where b.id = :id
            """)
    Optional<Booking> findDetailById(@Param("id") Long id);

    @Query("""
            select b from Booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
             order by b.tripDate desc
            """)
    List<Booking> findAllDetailed();

    /** Seats already committed for a package on a date; cancelled bookings release their seats. */
    @Query("""
            select coalesce(sum(b.participants), 0) from Booking b
             where b.safariPackage.id = :packageId
               and b.tripDate = :tripDate
               and b.status <> com.safari.tms.domain.enums.BookingStatus.CANCELLED
               and (:excludeBookingId is null or b.id <> :excludeBookingId)
            """)
    int sumCommittedSeats(@Param("packageId") Long packageId,
                          @Param("tripDate") LocalDate tripDate,
                          @Param("excludeBookingId") Long excludeBookingId);

    @Query("""
            select b from Booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
             where b.status = com.safari.tms.domain.enums.BookingStatus.CONFIRMED
               and not exists (
                     select 1 from Assignment a
                      where a.booking = b
                        and a.status <> com.safari.tms.domain.enums.AssignmentStatus.CANCELLED)
             order by b.tripDate asc
            """)
    List<Booking> findConfirmedAwaitingAssignment();

    List<Booking> findByStatusOrderByTripDateAsc(BookingStatus status);

    @Query("select b from Booking b where b.tripDate between :from and :to order by b.tripDate asc")
    List<Booking> findByTripDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatus(BookingStatus status);

    @Query("select count(b) from Booking b where b.tripDate >= :from and b.status in :statuses")
    long countUpcoming(@Param("from") LocalDate from, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
              join fetch b.safariPackage p
              join fetch p.park
              join fetch b.customer
             where b.status <> com.safari.tms.domain.enums.BookingStatus.CANCELLED
               and b.amountPaid < b.totalPrice
             order by b.paymentDueDate asc
            """)
    List<Booking> findWithOutstandingBalance();
}
