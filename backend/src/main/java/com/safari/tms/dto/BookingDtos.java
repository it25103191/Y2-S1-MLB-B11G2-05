package com.safari.tms.dto;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.enums.BookingStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class BookingDtos {

    public record BookingRequest(
            @NotNull(message = "Choose a safari package") Long packageId,

            @NotNull(message = "Choose a trip date")
            @Future(message = "Trip date must be in the future")
            LocalDate tripDate,

            @NotNull(message = "Number of participants is required")
            @Min(value = 1, message = "At least one participant is required")
            Integer participants,

            @Size(max = 1000) String specialRequests) {
    }

    /** Changes a pending booking. Capacity is re-checked and the price recalculated. */
    public record BookingUpdateRequest(
            @NotNull(message = "Choose a trip date")
            @Future(message = "Trip date must be in the future")
            LocalDate tripDate,

            @NotNull(message = "Number of participants is required")
            @Min(value = 1, message = "At least one participant is required")
            Integer participants,

            @Size(max = 1000) String specialRequests) {
    }

    public record CancelRequest(@Size(max = 500) String reason) {
    }

    public record StatusChangeRequest(@NotNull(message = "Status is required") BookingStatus status) {
    }

    public record BookingView(
            Long id,
            String bookingReference,
            Long customerId,
            String customerName,
            String customerEmail,
            Long packageId,
            String packageName,
            Long parkId,
            String parkName,
            Integer durationDays,
            LocalDate tripDate,
            LocalDate tripEndDate,
            Integer participants,
            BigDecimal pricePerPerson,
            BigDecimal totalPrice,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            boolean fullyPaid,
            BookingStatus status,
            String specialRequests,
            LocalDate paymentDueDate,
            boolean paymentOverdue,
            Instant createdAt,
            Instant cancelledAt,
            String cancellationReason,
            String packageImageUrl) {

        public static BookingView of(Booking b) {
            LocalDate end = b.getTripDate().plusDays(
                    Math.max(0, b.getSafariPackage().getDurationDays() - 1));
            boolean overdue = b.getPaymentDueDate() != null
                    && !b.isFullyPaid()
                    && b.getStatus() != BookingStatus.CANCELLED
                    && b.getPaymentDueDate().isBefore(LocalDate.now());

            return new BookingView(
                    b.getId(), b.getBookingReference(),
                    b.getCustomer().getId(), b.getCustomer().getFullName(), b.getCustomer().getEmail(),
                    b.getSafariPackage().getId(), b.getSafariPackage().getName(),
                    b.getSafariPackage().getPark().getId(), b.getSafariPackage().getPark().getName(),
                    b.getSafariPackage().getDurationDays(),
                    b.getTripDate(), end, b.getParticipants(),
                    b.getSafariPackage().getPricePerPerson(),
                    b.getTotalPrice(), b.getAmountPaid(), b.getBalanceDue(), b.isFullyPaid(),
                    b.getStatus(), b.getSpecialRequests(), b.getPaymentDueDate(), overdue,
                    b.getCreatedAt(), b.getCancelledAt(), b.getCancellationReason(),
                    b.getSafariPackage().getImageUrl());
        }
    }

    /** Price preview shown while the customer is still filling in the booking form. */
    public record QuoteView(
            Long packageId,
            String packageName,
            LocalDate tripDate,
            int participants,
            BigDecimal pricePerPerson,
            BigDecimal totalPrice,
            int seatsRemaining,
            boolean available,
            String message) {
    }

    /**
     * One entry on a booking's timeline.
     *
     * @param actorName who made the change, as this viewer should see it ("You", a staff name, ...)
     * @param actorRole the staff role, shown to staff only
     * @param derived   true when worked out from the booking itself, for bookings made before the
     *                  timeline existed
     */
    public record BookingHistoryView(
            Long id,
            String event,
            String title,
            String detail,
            BookingStatus status,
            String actorName,
            String actorRole,
            Instant at,
            boolean derived) {
    }

    private BookingDtos() {
    }
}
