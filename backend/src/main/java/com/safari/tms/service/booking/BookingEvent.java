package com.safari.tms.service.booking;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;

import java.time.LocalDate;

/**
 * Something that happened to a booking. This is the "message" the subject passes to every observer.
 *
 * @param actor              who caused it, or {@code null} for system actions
 * @param previousTripDate   the departure before an UPDATED change, otherwise {@code null}
 * @param previousTravellers the party size before an UPDATED change, otherwise {@code null}
 * @param previousStatus     the status before a STATUS_CHANGED change, otherwise {@code null}
 * @param note               why a STATUS_CHANGED change happened (for example "Paid in full"), or {@code null}
 */
public record BookingEvent(Type type, Booking booking, User actor,
                           LocalDate previousTripDate, Integer previousTravellers,
                           BookingStatus previousStatus, String note) {

    public enum Type {
        /** A customer reserved seats. */
        CREATED,
        /** A pending booking's date or party size changed. */
        UPDATED,
        /** The customer or staff cancelled it through the cancel action. */
        CANCELLED,
        /** Its status moved for another reason: set by staff, or confirmed and reopened by payments. */
        STATUS_CHANGED,
        /** An unpaid booking was deleted outright. */
        REMOVED
    }

    public static BookingEvent created(Booking booking, User customer) {
        return new BookingEvent(Type.CREATED, booking, customer, null, null, null, null);
    }

    public static BookingEvent updated(Booking booking, User actor, LocalDate oldDate, int oldTravellers) {
        return new BookingEvent(Type.UPDATED, booking, actor, oldDate, oldTravellers, null, null);
    }

    public static BookingEvent cancelled(Booking booking, User actor) {
        return new BookingEvent(Type.CANCELLED, booking, actor, null, null, null, null);
    }

    public static BookingEvent statusChanged(Booking booking, User actor, BookingStatus previousStatus, String note) {
        return new BookingEvent(Type.STATUS_CHANGED, booking, actor, null, null, previousStatus, note);
    }

    public static BookingEvent removed(Booking booking, User actor) {
        return new BookingEvent(Type.REMOVED, booking, actor, null, null, null, null);
    }

    /** True when the booking ended up cancelled, whichever way that happened. */
    public boolean isCancellation() {
        return type == Type.CANCELLED
                || (type == Type.STATUS_CHANGED && booking.getStatus() == BookingStatus.CANCELLED);
    }
}
