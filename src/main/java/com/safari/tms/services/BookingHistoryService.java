package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.BookingHistory;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.dto.BookingDtos.BookingHistoryView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingHistoryRepository;
import com.safari.tms.repo.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads a booking's timeline. The entries are written by {@code BookingHistoryRecorder}, one of
 * the booking observers; this class only puts them in order and decides how names are shown.
 */
@Service
public class BookingHistoryService {

    private final BookingRepository bookings;
    private final BookingHistoryRepository history;

    public BookingHistoryService(BookingRepository bookings, BookingHistoryRepository history) {
        this.bookings = bookings;
        this.history = history;
    }

    /** The caller must already be allowed to see the booking (the controller checks that first). */
    @Transactional(readOnly = true)
    public List<BookingHistoryView> forBooking(Long bookingId, User viewer) {
        Booking booking = bookings.findDetailById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking", bookingId));
        List<BookingHistory> entries = history.findForBooking(bookingId);

        List<BookingHistoryView> timeline = new ArrayList<>();
        for (BookingHistory h : entries) {
            timeline.add(new BookingHistoryView(h.getId(), h.getEvent(), h.getTitle(), h.getDetail(), h.getStatus(),
                    actorName(h.getActor(), viewer), actorRole(h.getActor(), viewer), h.getCreatedAt(), false));
        }

        // Bookings made before the timeline existed have no entries yet, so fill in what the
        // booking itself records.
        if (entries.stream().noneMatch(h -> "CREATED".equals(h.getEvent()))) {
            timeline.add(derived("CREATED", "Booked", "Booking " + booking.getBookingReference() + " created.",
                    BookingStatus.PENDING, booking.getCustomer(), viewer, booking.getCreatedAt()));
        }
        if (booking.getStatus() == BookingStatus.CANCELLED && booking.getCancelledAt() != null
                && entries.stream().noneMatch(h -> h.getStatus() == BookingStatus.CANCELLED)) {
            timeline.add(derived("CANCELLED", "Cancelled", booking.getCancellationReason(),
                    BookingStatus.CANCELLED, null, viewer, booking.getCancelledAt()));
        }

        timeline.sort(Comparator.comparing(BookingHistoryView::at));
        return timeline;
    }

    private BookingHistoryView derived(String event, String title, String detail, BookingStatus status,
                                       User actor, User viewer, Instant at) {
        return new BookingHistoryView(null, event, title, detail, status,
                actorName(actor, viewer), actorRole(actor, viewer), at, true);
    }

    /** Customers see "You" and "Ceylon Trails team"; staff see real names. */
    private static String actorName(User actor, User viewer) {
        if (actor == null) {
            return "Automatic";
        }
        if (actor.getId().equals(viewer.getId())) {
            return "You";
        }
        if (!viewer.getRole().isStaff() && actor.getRole().isStaff()) {
            return "Ceylon Trails team";
        }
        return actor.getFullName();
    }

    private static String actorRole(User actor, User viewer) {
        return actor != null && viewer.getRole().isStaff() ? actor.getRole().getLabel() : null;
    }
}
