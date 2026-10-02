package com.safari.tms.service;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.SafariPackage;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.BookingDtos.BookingRequest;
import com.safari.tms.dto.BookingDtos.BookingView;
import com.safari.tms.dto.BookingDtos.QuoteView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.SafariPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    public BookingService(BookingRepository bookings,
                          SafariPackageRepository packages,
                          ReferenceGenerator references,
                          NotificationService notifications,
                          RefundService refundService) {
        this.bookings = bookings;
        this.packages = packages;
        this.references = references;
        this.notifications = notifications;
        this.refundService = refundService;
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

        int taken = bookings.sumCommittedSeats(pkg.getId(), request.tripDate(), null);
        int remaining = pkg.getMaxGroupSize() - taken;
        if (request.participants() > remaining) {
            throw ApiException.conflict(
                    remaining <= 0
                            ? "'" + pkg.getName() + "' is fully booked on " + request.tripDate() + "."
                            : "Only " + remaining + " seat(s) left on " + request.tripDate()
                              + " for '" + pkg.getName() + "'. You asked for " + request.participants() + ".",
                    Map.of("packageId", pkg.getId(),
                            "tripDate", request.tripDate().toString(),
                            "maxGroupSize", pkg.getMaxGroupSize(),
                            "seatsTaken", taken,
                            "seatsRemaining", Math.max(0, remaining),
                            "requested", request.participants()));
        }

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
        SafariPackage pkg = packages.findDetailById(packageId);
        if (pkg == null) {
            throw ApiException.notFound("Safari package", packageId);
        }
        int safeParticipants = Math.max(1, participants);
        int taken = bookings.sumCommittedSeats(packageId, tripDate, null);
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
