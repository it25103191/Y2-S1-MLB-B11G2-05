package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.Refund;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.PaymentStatus;
import com.safari.tms.domain.enums.RefundStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.FinanceDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.PaymentRepository;
import com.safari.tms.repo.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Cancellation-window refund policy.
 *
 * <pre>
 *   more than 7 days before departure ... 100% of everything paid
 *   2 to 7 days before departure ........  50%
 *   inside 48 hours, or after departure .   0%
 * </pre>
 *
 * The window is measured in whole days between today and the departure date.
 */
@Service
public class RefundService {

    private static final int FULL_REFUND_DAYS = 7;
    private static final int PARTIAL_REFUND_DAYS = 2;
    private static final int PARTIAL_REFUND_PERCENT = 50;

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final ReferenceGenerator references;
    private final NotificationService notifications;

    public RefundService(RefundRepository refunds,
                         PaymentRepository payments,
                         BookingRepository bookings,
                         ReferenceGenerator references,
                         NotificationService notifications) {
        this.refunds = refunds;
        this.payments = payments;
        this.bookings = bookings;
        this.references = references;
        this.notifications = notifications;
    }

    /* ------------------------------------------------------------- Policy */

    private record Policy(int percentage, String name, String explanation) {
    }

    private Policy policyFor(long daysBeforeTrip) {
        if (daysBeforeTrip > FULL_REFUND_DAYS) {
            return new Policy(100, "Full refund (more than 7 days before departure)",
                    "Cancelled " + daysBeforeTrip + " days before departure, which is more than the "
                            + FULL_REFUND_DAYS + "-day threshold, so the full amount paid is returned.");
        }
        if (daysBeforeTrip >= PARTIAL_REFUND_DAYS) {
            return new Policy(PARTIAL_REFUND_PERCENT, "Half refund (within 7 days of departure)",
                    "Cancelled " + daysBeforeTrip + " days before departure, inside the "
                            + FULL_REFUND_DAYS + "-day window but outside 48 hours, so "
                            + PARTIAL_REFUND_PERCENT + "% is returned.");
        }
        if (daysBeforeTrip >= 0) {
            return new Policy(0, "No refund (within 48 hours of departure)",
                    "Cancelled " + daysBeforeTrip + " day(s) before departure, inside the 48-hour "
                            + "window, so no refund is due.");
        }
        return new Policy(0, "No refund (departure has passed)",
                "The departure date passed " + Math.abs(daysBeforeTrip) + " day(s) ago, so no refund is due.");
    }

    /* -------------------------------------------------------------- Quote */

    @Transactional(readOnly = true)
    public RefundQuoteView quote(Long bookingId, User caller) {
        Booking booking = requireVisible(bookingId, caller);
        return buildQuote(booking);
    }

    private RefundQuoteView buildQuote(Booking booking) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), booking.getTripDate());
        Policy policy = policyFor(days);

        BigDecimal paid = booking.getAmountPaid() == null ? BigDecimal.ZERO : booking.getAmountPaid();
        BigDecimal amount = paid.multiply(BigDecimal.valueOf(policy.percentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        boolean already = refunds.existsByBookingIdAndStatusIn(booking.getId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSED));

        return new RefundQuoteView(
                booking.getId(), booking.getBookingReference(), booking.getCustomer().getFullName(),
                booking.getTripDate(), days, paid, policy.percentage(), amount,
                policy.name(), policy.explanation(),
                paid.compareTo(BigDecimal.ZERO) > 0 && !already,
                already);
    }

    /* ------------------------------------------------------------ Request */

    @Transactional
    public RefundView request(RefundRequestBody body, User caller) {
        Booking booking = requireVisible(body.bookingId(), caller);

        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw ApiException.badRequest("Only cancelled bookings can be refunded. Cancelling booking "
                    + booking.getBookingReference() + " raises a refund request automatically.");
        }
        if (refunds.existsByBookingIdAndStatusIn(booking.getId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSED))) {
            throw ApiException.conflict("Booking " + booking.getBookingReference()
                    + " already has an open or completed refund.");
        }

        Refund created = createFor(booking, body.reason(), caller);
        if (created == null) {
            throw ApiException.badRequest("Nothing has been paid on " + booking.getBookingReference()
                    + ", so there is nothing to refund.");
        }
        return RefundView.of(refunds.findDetailById(created.getId()).orElse(created));
    }

    /**
     * Raises a refund request automatically when a booking with money against it is cancelled.
     * Returns {@code null} when nothing was paid or a refund already exists.
     */
    @Transactional
    public Refund createFor(Booking booking, String reason, User actor) {
        BigDecimal paid = booking.getAmountPaid() == null ? BigDecimal.ZERO : booking.getAmountPaid();
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (refunds.existsByBookingIdAndStatusIn(booking.getId(),
                List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSED))) {
            return null;
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(), booking.getTripDate());
        Policy policy = policyFor(days);
        BigDecimal amount = paid.multiply(BigDecimal.valueOf(policy.percentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Refund refund = new Refund();
        refund.setRefundReference(references.refund());
        refund.setBooking(booking);
        refund.setAmountPaidAtRequest(paid);
        refund.setCalculatedAmount(amount);
        refund.setRefundPercentage(policy.percentage());
        refund.setPolicyApplied(policy.name());
        refund.setDaysBeforeTrip((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, days)));
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setReason(reason == null || reason.isBlank() ? "Booking cancelled" : reason.trim());

        Refund saved = refunds.save(refund);

        notifications.email(booking.getCustomer(),
                "Refund request " + saved.getRefundReference() + " raised",
                "We have logged a refund request for " + booking.getBookingReference()
                        + ". Under our cancellation policy (" + policy.name() + ") the amount due is "
                        + amount + ". Our finance team will confirm shortly.",
                "Refund", saved.getId());

        return saved;
    }

    /* ----------------------------------------------------------- Decision */

    @Transactional
    public RefundView approve(Long id, RefundDecisionRequest request, User actor) {
        Refund refund = require(id);

        if (refund.getStatus() == RefundStatus.PROCESSED) {
            throw ApiException.badRequest("This refund has already been paid out.");
        }
        if (refund.getStatus() == RefundStatus.REJECTED) {
            throw ApiException.badRequest("This refund was rejected. Raise a new request instead.");
        }

        BigDecimal fallback = refund.getAdjustedAmount() != null
                ? refund.getAdjustedAmount()
                : refund.getCalculatedAmount();
        BigDecimal approved = request != null && request.approvedAmount() != null
                ? request.approvedAmount().setScale(2, RoundingMode.HALF_UP)
                : fallback;

        if (approved.compareTo(refund.getAmountPaidAtRequest()) > 0) {
            throw ApiException.badRequest("Cannot refund more than the "
                    + refund.getAmountPaidAtRequest() + " the customer actually paid.");
        }

        refund.setApprovedAmount(approved);
        refund.setStatus(RefundStatus.APPROVED);
        refund.setProcessedBy(actor);
        if (request != null && request.decisionNotes() != null && !request.decisionNotes().isBlank()) {
            refund.setDecisionNotes(request.decisionNotes().trim());
        }

        return RefundView.of(refunds.save(refund));
    }

    @Transactional
    public RefundView reject(Long id, RefundDecisionRequest request, User actor) {
        Refund refund = require(id);
        if (refund.getStatus() == RefundStatus.PROCESSED) {
            throw ApiException.badRequest("This refund has already been paid out.");
        }
        refund.setStatus(RefundStatus.REJECTED);
        refund.setApprovedAmount(BigDecimal.ZERO);
        refund.setProcessedBy(actor);
        refund.setProcessedAt(Instant.now());
        if (request != null && request.decisionNotes() != null && !request.decisionNotes().isBlank()) {
            refund.setDecisionNotes(request.decisionNotes().trim());
        }

        Refund saved = refunds.save(refund);
        notifications.email(saved.getBooking().getCustomer(),
                "Refund " + saved.getRefundReference() + " declined",
                "After review we are unable to refund this booking. "
                        + (saved.getDecisionNotes() == null ? "" : saved.getDecisionNotes()),
                "Refund", saved.getId());

        return RefundView.of(saved);
    }

    /** Pays the refund out: reduces the amount held against the booking and closes the record. */
    @Transactional
    public RefundView process(Long id, User actor) {
        Refund refund = require(id);

        if (refund.getStatus() != RefundStatus.APPROVED) {
            throw ApiException.badRequest("Approve the refund before processing it.");
        }

        BigDecimal amount = refund.getApprovedAmount() == null
                ? refund.getCalculatedAmount() : refund.getApprovedAmount();

        Booking booking = refund.getBooking();
        BigDecimal newPaid = booking.getAmountPaid().subtract(amount);
        booking.setAmountPaid(newPaid.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newPaid);
        bookings.save(booking);

        // Mark settled payments as refunded once the whole balance has been returned.
        if (booking.getAmountPaid().compareTo(BigDecimal.ZERO) == 0) {
            payments.findForBooking(booking.getId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .forEach(p -> {
                        p.setStatus(PaymentStatus.REFUNDED);
                        payments.save(p);
                    });
        }

        refund.setStatus(RefundStatus.PROCESSED);
        refund.setProcessedAt(Instant.now());
        refund.setProcessedBy(actor);

        Refund saved = refunds.save(refund);

        notifications.email(booking.getCustomer(),
                "Refund " + saved.getRefundReference() + " processed",
                "A refund of " + amount + " for booking " + booking.getBookingReference()
                        + " has been released and should reach your account within 5 working days.",
                "Refund", saved.getId());

        return RefundView.of(saved);
    }

    /* -------------------------------------------------------- Edit / withdraw */

    /** Adjusts a refund before a decision is made. The policy figure is kept for reference. */
    @Transactional
    public RefundView update(Long id, RefundUpdateRequest request) {
        Refund refund = require(id);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw ApiException.badRequest("Only refunds still awaiting a decision can be edited. This one is "
                    + refund.getStatus().name().toLowerCase() + ".");
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(refund.getAmountPaidAtRequest()) > 0) {
            throw ApiException.badRequest("Cannot refund more than the "
                    + refund.getAmountPaidAtRequest() + " the customer actually paid.");
        }

        // Matching the policy amount again simply clears the adjustment.
        refund.setAdjustedAmount(amount.compareTo(refund.getCalculatedAmount()) == 0 ? null : amount);
        if (request.reason() != null) {
            refund.setReason(request.reason().isBlank() ? null : request.reason().trim());
        }
        if (request.decisionNotes() != null) {
            refund.setDecisionNotes(request.decisionNotes().isBlank() ? null : request.decisionNotes().trim());
        }

        return RefundView.of(refunds.save(refund));
    }

    /** Withdraws a refund request that has not been decided yet. */
    @Transactional
    public void withdraw(Long id) {
        Refund refund = require(id);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw ApiException.badRequest("Only refunds still awaiting a decision can be withdrawn. Approved"
                    + " refunds must be paid out, and rejected ones stay on record.");
        }

        Booking booking = refund.getBooking();
        String reference = refund.getRefundReference();
        refunds.delete(refund);

        notifications.email(booking.getCustomer(), "Refund request " + reference + " withdrawn",
                "The refund request on booking " + booking.getBookingReference()
                        + " has been withdrawn. Contact us if you think this is a mistake.",
                "Refund", refund.getId());
    }

    /* ------------------------------------------------------------ Queries */

    @Transactional(readOnly = true)
    public List<RefundView> findAll() {
        return refunds.findAllDetailed().stream().map(RefundView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RefundView> findForBooking(Long bookingId, User caller) {
        Booking booking = requireVisible(bookingId, caller);
        return refunds.findForBooking(booking.getId()).stream().map(RefundView::of).toList();
    }

    @Transactional(readOnly = true)
    public RefundView findOne(Long id) {
        return RefundView.of(require(id));
    }

    private Refund require(Long id) {
        return refunds.findDetailById(id).orElseThrow(() -> ApiException.notFound("Refund", id));
    }

    private Booking requireVisible(Long bookingId, User caller) {
        Booking booking = bookings.findDetailById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking", bookingId));
        if (caller.getRole() == Role.CUSTOMER && !booking.getCustomer().getId().equals(caller.getId())) {
            throw ApiException.forbidden("That booking belongs to another customer.");
        }
        return booking;
    }
}
