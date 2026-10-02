package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.SafariPackage;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.AssignmentStatus;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.PermitStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.BookingDtos.BookingRequest;
import com.safari.tms.dto.BookingDtos.BookingUpdateRequest;
import com.safari.tms.dto.BookingDtos.BookingView;
import com.safari.tms.dto.BookingDtos.QuoteView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.AssignmentRepository;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.ComplaintRepository;
import com.safari.tms.repo.PaymentRepository;
import com.safari.tms.repo.PermitRepository;
import com.safari.tms.repo.RefundRepository;
import com.safari.tms.repo.SafariPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    /** Balance is expected this many days before departure. */
    private static final int PAYMENT_DUE_DAYS_BEFORE_TRIP = 7;

    private final BookingRepository bookings;
    private final SafariPackageRepository packages;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final RefundService refundService;
    private final AssignmentRepository assignments;
    private final PermitRepository permits;
    private final PaymentRepository payments;
    private final ComplaintRepository complaints;
    private final RefundRepository refunds;

    public BookingService(BookingRepository bookings,
                          SafariPackageRepository packages,
                          ReferenceGenerator references,
                          NotificationService notifications,
                          RefundService refundService,
                          AssignmentRepository assignments,
                          PermitRepository permits,
                          PaymentRepository payments,
                          ComplaintRepository complaints,
                          RefundRepository refunds) {
        this.bookings = bookings;
        this.packages = packages;
        this.references = references;
        this.notifications = notifications;
        this.refundService = refundService;
        this.assignments = assignments;
        this.permits = permits;
        this.payments = payments;
        this.complaints = complaints;
        this.refunds = refunds;
    }

    /**
     * Creates a booking after an atomic capacity check. The package row is locked for the duration
     * of the transaction so two concurrent bookings cannot jointly exceed {@code maxGroupSize}.
     */
    @Transactional
    public BookingView create(User customer, BookingRequest request) {
        SafariPackage pkg = packages.findByIdForUpdate(request.packageId())
                .orElseThrow(() -> ApiException.notFound("Safari package", request.packageId()));

        if (!pkg.isActive()) {
            throw ApiException.badRequest("'" + pkg.getName() + "' is no longer open for booking.");
        }
        if (!request.tripDate().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Trip date must be in the future.");
        }
        if (request.participants() > pkg.getMaxGroupSize()) {
            throw ApiException.badRequest("This package takes at most " + pkg.getMaxGroupSize()
                    + " travellers per departure.");
        }

        ensureCapacity(pkg, request.tripDate(), request.participants(), null);

        Booking booking = new Booking();
        booking.setBookingReference(references.booking());
        booking.setCustomer(customer);
        booking.setSafariPackage(pkg);
        booking.setTripDate(request.tripDate());
        booking.setParticipants(request.participants());
        booking.setTotalPrice(pkg.getPricePerPerson().multiply(BigDecimal.valueOf(request.participants())));
        booking.setAmountPaid(BigDecimal.ZERO);
        booking.setStatus(BookingStatus.PENDING);
        booking.setSpecialRequests(request.specialRequests());
        booking.setPaymentDueDate(derivePaymentDueDate(request.tripDate()));

        Booking saved = bookings.save(booking);

        notifications.email(customer, "Booking " + saved.getBookingReference() + " received",
                "Thanks " + customer.getFullName() + ", we are holding " + saved.getParticipants()
                        + " place(s) on '" + pkg.getName() + "' for " + saved.getTripDate()
                        + ". Your balance of " + saved.getTotalPrice()
                        + " is due by " + saved.getPaymentDueDate() + ".",
                "Booking", saved.getId());

        return BookingView.of(saved);
    }

    /** Pricing and availability preview used by the booking form before anything is persisted. */
    @Transactional(readOnly = true)
    public QuoteView quote(Long packageId, LocalDate tripDate, int participants) {
        return quote(packageId, tripDate, participants, null);
    }

    /**
     * Same as {@link #quote(Long, LocalDate, int)} but ignores one booking's own seats, so the
     * edit-booking form shows availability as if that booking were being made afresh.
     */
    @Transactional(readOnly = true)
    public QuoteView quote(Long packageId, LocalDate tripDate, int participants, Long excludeBookingId) {
        SafariPackage pkg = packages.findDetailById(packageId);
        if (pkg == null) {
            throw ApiException.notFound("Safari package", packageId);
        }
        int safeParticipants = Math.max(1, participants);
        int taken = bookings.sumCommittedSeats(packageId, tripDate, excludeBookingId);
        int remaining = Math.max(0, pkg.getMaxGroupSize() - taken);
        boolean available = safeParticipants <= remaining && tripDate.isAfter(LocalDate.now()) && pkg.isActive();

        String message;
        if (!pkg.isActive()) {
            message = "This package is not currently open for booking.";
        } else if (!tripDate.isAfter(LocalDate.now())) {
            message = "Choose a departure date in the future.";
        } else if (remaining == 0) {
            message = "Fully booked on this date.";
        } else if (safeParticipants > remaining) {
            message = "Only " + remaining + " seat(s) left on this date.";
        } else {
            message = remaining + " of " + pkg.getMaxGroupSize() + " seats available.";
        }

        return new QuoteView(pkg.getId(), pkg.getName(), tripDate, safeParticipants,
                pkg.getPricePerPerson(),
                pkg.getPricePerPerson().multiply(BigDecimal.valueOf(safeParticipants)),
                remaining, available, message);
    }

    @Transactional(readOnly = true)
    public List<BookingView> findForCustomer(Long customerId) {
        return bookings.findForCustomer(customerId).stream().map(BookingView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingView> findAll() {
        return bookings.findAllDetailed().stream().map(BookingView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingView> findAwaitingAssignment() {
        return bookings.findConfirmedAwaitingAssignment().stream().map(BookingView::of).toList();
    }

    @Transactional(readOnly = true)
    public BookingView findOne(Long id, User caller) {
        Booking booking = requireVisible(id, caller);
        return BookingView.of(booking);
    }

    @Transactional
    public BookingView cancel(Long id, User caller, String reason) {
        Booking booking = requireVisible(id, caller);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("This booking is already cancelled.");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw ApiException.badRequest("Completed trips cannot be cancelled.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason == null || reason.isBlank()
                ? "Cancelled by " + (caller.getRole() == Role.CUSTOMER ? "customer" : "staff")
                : reason.trim());

        Booking saved = bookings.save(booking);

        // Money already taken triggers a refund request assessed against the cancellation policy.
        refundService.createFor(saved, "Booking cancelled: " + saved.getCancellationReason(), caller);

        notifications.email(booking.getCustomer(), "Booking " + saved.getBookingReference() + " cancelled",
                "Your booking for '" + saved.getSafariPackage().getName() + "' on " + saved.getTripDate()
                        + " has been cancelled. " + (saved.getAmountPaid().compareTo(BigDecimal.ZERO) > 0
                        ? "Any refund due will be assessed against our cancellation policy."
                        : "No payment had been taken."),
                "Booking", saved.getId());

        return BookingView.of(saved);
    }

    /**
     * Changes a pending booking's date, party size or special requests. The package row is locked
     * and the capacity check re-run without this booking's own seats, then the price is recalculated.
     */
    @Transactional
    public BookingView update(Long id, User caller, BookingUpdateRequest request) {
        Booking booking = requireVisible(id, caller);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw ApiException.badRequest("Only pending bookings can be changed. Booking "
                    + booking.getBookingReference() + " is " + booking.getStatus().name().toLowerCase()
                    + " — cancel it and book again instead.");
        }

        boolean dateChanged = !booking.getTripDate().equals(request.tripDate());
        boolean sizeChanged = !booking.getParticipants().equals(request.participants());

        if (dateChanged || sizeChanged) {
            List<String> locks = new ArrayList<>();
            assignments.findByBookingId(id)
                    .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                    .ifPresent(a -> locks.add("a guide and vehicle are assigned to it"));
            if (permits.existsByBookingIdAndStatusIn(id, List.of(PermitStatus.PENDING, PermitStatus.APPROVED))) {
                locks.add("a park permit already covers it");
            }
            if (!locks.isEmpty()) {
                throw ApiException.conflict("The date and party size are locked because "
                        + String.join(" and ", locks)
                        + ". Release those first, or change only the special requests.");
            }
        }

        Long packageId = booking.getSafariPackage().getId();
        SafariPackage pkg = packages.findByIdForUpdate(packageId)
                .orElseThrow(() -> ApiException.notFound("Safari package", packageId));

        if (dateChanged && !pkg.isActive()) {
            throw ApiException.badRequest("'" + pkg.getName()
                    + "' is no longer open for booking, so the date cannot be moved.");
        }
        if (request.participants() > pkg.getMaxGroupSize()) {
            throw ApiException.badRequest("This package takes at most " + pkg.getMaxGroupSize()
                    + " travellers per departure.");
        }
        if (dateChanged || sizeChanged) {
            ensureCapacity(pkg, request.tripDate(), request.participants(), booking.getId());
        }

        BigDecimal newTotal = pkg.getPricePerPerson().multiply(BigDecimal.valueOf(request.participants()));
        if (booking.getAmountPaid().compareTo(newTotal) > 0) {
            throw ApiException.badRequest("You have already paid " + booking.getAmountPaid()
                    + ", which is more than the new total of " + newTotal
                    + ". Keep more travellers on the booking, or cancel it for a refund.");
        }

        LocalDate oldDate = booking.getTripDate();
        int oldSize = booking.getParticipants();

        booking.setTripDate(request.tripDate());
        booking.setParticipants(request.participants());
        booking.setTotalPrice(newTotal);
        booking.setSpecialRequests(request.specialRequests() == null || request.specialRequests().isBlank()
                ? null : request.specialRequests().trim());
        if (dateChanged) {
            booking.setPaymentDueDate(derivePaymentDueDate(request.tripDate()));
        }

        Booking saved = bookings.save(booking);

        if (dateChanged || sizeChanged) {
            notifications.email(saved.getCustomer(), "Booking " + saved.getBookingReference() + " updated",
                    "Your booking for '" + pkg.getName() + "' has changed from " + oldSize + " traveller(s) on "
                            + oldDate + " to " + saved.getParticipants() + " traveller(s) on " + saved.getTripDate()
                            + ". The new total is " + saved.getTotalPrice() + ", due by "
                            + saved.getPaymentDueDate() + ".",
                    "Booking", saved.getId());
        }

        return BookingView.of(saved);
    }

    /**
     * Permanently removes a booking that never got going: still pending, nothing paid and nothing
     * else attached to it. Anything further along keeps its history and must be cancelled instead.
     */
    @Transactional
    public void delete(Long id, User caller) {
        Booking booking = requireVisible(id, caller);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw ApiException.conflict("Only pending bookings can be deleted. Booking "
                    + booking.getBookingReference() + " is " + booking.getStatus().name().toLowerCase()
                    + ", so cancel it instead to keep its history.");
        }

        List<String> blockers = new ArrayList<>();
        long paymentRecords = payments.countByBookingId(id);
        if (booking.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            blockers.add(booking.getAmountPaid() + " has been paid against it");
        } else if (paymentRecords > 0) {
            blockers.add("it has " + paymentRecords + " failed payment attempt(s) on record"
                    + " (Finance can delete those)");
        }
        if (assignments.findByBookingId(id).isPresent()) {
            blockers.add("a crew assignment exists");
        }
        if (permits.countByBookingId(id) > 0) {
            blockers.add("a park permit exists");
        }
        if (complaints.countByBookingId(id) > 0) {
            blockers.add("a customer case refers to it");
        }
        if (refunds.countByBookingId(id) > 0) {
            blockers.add("a refund record exists");
        }
        if (!blockers.isEmpty()) {
            throw ApiException.conflict("Booking " + booking.getBookingReference() + " can't be deleted because "
                    + String.join("; ", blockers) + ". Cancel it instead.");
        }

        User customer = booking.getCustomer();
        String reference = booking.getBookingReference();
        String packageName = booking.getSafariPackage().getName();
        Long bookingId = booking.getId();

        bookings.delete(booking);

        notifications.email(customer, "Booking " + reference + " removed",
                "Your unpaid booking for '" + packageName + "' has been removed and its seats released.",
                "Booking", bookingId);
    }

    /** Staff-driven status change (for example marking a trip completed). */
    @Transactional
    public BookingView changeStatus(Long id, BookingStatus status) {
        Booking booking = bookings.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Booking", id));

        if (booking.getStatus() == BookingStatus.CANCELLED && status != BookingStatus.CANCELLED) {
            throw ApiException.badRequest("A cancelled booking cannot be reopened.");
        }
        if (status == BookingStatus.CONFIRMED && booking.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(
                    "Take at least a deposit before confirming booking " + booking.getBookingReference() + ".");
        }

        booking.setStatus(status);
        if (status == BookingStatus.CANCELLED && booking.getCancelledAt() == null) {
            booking.setCancelledAt(Instant.now());
            booking.setCancellationReason("Cancelled by staff");
        }
        Booking saved = bookings.save(booking);
        if (status == BookingStatus.CANCELLED) {
            refundService.createFor(saved, "Booking cancelled by staff", null);
        }
        return BookingView.of(saved);
    }

    @Transactional(readOnly = true)
    public Booking require(Long id) {
        return bookings.findDetailById(id).orElseThrow(() -> ApiException.notFound("Booking", id));
    }

    /** Customers may only ever touch their own bookings; staff can see everything. */
    /** Throws a 409 with seat figures when {@code requested} seats would exceed the departure's cap. */
    private void ensureCapacity(SafariPackage pkg, LocalDate tripDate, int requested, Long excludeBookingId) {
        int taken = bookings.sumCommittedSeats(pkg.getId(), tripDate, excludeBookingId);
        int remaining = pkg.getMaxGroupSize() - taken;
        if (requested > remaining) {
            throw ApiException.conflict(
                    remaining <= 0
                            ? "'" + pkg.getName() + "' is fully booked on " + tripDate + "."
                            : "Only " + remaining + " seat(s) left on " + tripDate
                              + " for '" + pkg.getName() + "'. You asked for " + requested + ".",
                    Map.of("packageId", pkg.getId(),
                            "tripDate", tripDate.toString(),
                            "maxGroupSize", pkg.getMaxGroupSize(),
                            "seatsTaken", taken,
                            "seatsRemaining", Math.max(0, remaining),
                            "requested", requested));
        }
    }

    private Booking requireVisible(Long id, User caller) {
        Booking booking = require(id);
        if (caller.getRole() == Role.CUSTOMER && !booking.getCustomer().getId().equals(caller.getId())) {
            throw ApiException.forbidden("This booking belongs to another customer.");
        }
        return booking;
    }

    private LocalDate derivePaymentDueDate(LocalDate tripDate) {
        LocalDate due = tripDate.minusDays(PAYMENT_DUE_DAYS_BEFORE_TRIP);
        LocalDate earliest = LocalDate.now().plusDays(2);
        return due.isBefore(earliest) ? earliest.isAfter(tripDate) ? tripDate : earliest : due;
    }
}
