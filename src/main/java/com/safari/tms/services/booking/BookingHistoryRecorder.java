package com.safari.tms.service.booking;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.BookingHistory;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.repo.BookingHistoryRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Concrete observer: writes every booking change to the booking's timeline.
 *
 * <p>This class was added without changing BookingService or the other observers. That is the
 * point of the Observer pattern: the subject does not know who is listening, so a new reaction to
 * bookings is just a new observer.
 */
@Component
@Order(3)
public class BookingHistoryRecorder implements BookingObserver {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final BookingHistoryRepository history;

    public BookingHistoryRecorder(BookingHistoryRepository history) {
        this.history = history;
    }

    @Override
    public void update(BookingEvent event) {
        Booking b = event.booking();
        switch (event.type()) {
            case CREATED -> record(event, "Booked", b.getParticipants() + " traveller(s) on " + day(b.getTripDate())
                    + ". Total " + usd(b.getTotalPrice()) + ", due by " + day(b.getPaymentDueDate()) + ".");

            case UPDATED -> record(event, "Trip changed", changes(event));

            case CANCELLED -> record(event, "Cancelled", b.getCancellationReason());

            case STATUS_CHANGED -> {
                if (event.previousStatus() != b.getStatus()) {
                    record(event, titleFor(b.getStatus()), "From " + label(event.previousStatus()) + " to "
                            + label(b.getStatus()) + (event.note() == null ? "." : ". " + event.note() + "."));
                }
            }

            // The booking and its timeline are deleted together, so there is nothing to write.
            case REMOVED -> { }
        }
    }

    private void record(BookingEvent event, String title, String detail) {
        history.save(new BookingHistory(event.booking(), event.type().name(), title, detail, event.actor()));
    }

    private static String changes(BookingEvent event) {
        Booking b = event.booking();
        List<String> parts = new ArrayList<>();
        if (!b.getTripDate().equals(event.previousTripDate())) {
            parts.add("Trip date moved from " + day(event.previousTripDate()) + " to " + day(b.getTripDate()));
        }
        if (!b.getParticipants().equals(event.previousTravellers())) {
            parts.add("Travellers changed from " + event.previousTravellers() + " to " + b.getParticipants());
        }
        parts.add("New total " + usd(b.getTotalPrice()));
        return String.join(". ", parts) + ".";
    }

    private static String titleFor(BookingStatus status) {
        return switch (status) {
            case PENDING -> "Reopened";
            case CONFIRMED -> "Confirmed";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String label(BookingStatus status) {
        return status == null ? "unknown" : status.name().charAt(0) + status.name().substring(1).toLowerCase();
    }

    private static String day(LocalDate date) {
        return date == null ? "—" : date.format(DAY);
    }

    private static String usd(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "USD %,.2f", amount);
    }
}
