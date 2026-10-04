package com.safari.tms.repo;

import com.safari.tms.domain.BookingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingHistoryRepository extends JpaRepository<BookingHistory, Long> {

    @Query("""
            select h from BookingHistory h
              left join fetch h.actor
             where h.booking.id = :bookingId
             order by h.createdAt asc, h.id asc
            """)
    List<BookingHistory> findForBooking(@Param("bookingId") Long bookingId);
}
