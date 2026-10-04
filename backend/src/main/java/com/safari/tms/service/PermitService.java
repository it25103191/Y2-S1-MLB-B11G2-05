package com.safari.tms.service;

import com.safari.tms.config.AppProperties;
import com.safari.tms.domain.Booking;
import com.safari.tms.domain.Permit;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.PermitStatus;
import com.safari.tms.dto.PermitDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.PermitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
public class PermitService {

    /** Days of validity added after the trip ends when no expiry date is supplied. */
    private static final int DEFAULT_GRACE_DAYS = 7;

    private final PermitRepository permits;
    private final BookingRepository bookings;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final AppProperties props;

    public PermitService(PermitRepository permits,
                         BookingRepository bookings,
                         ReferenceGenerator references,
                         NotificationService notifications,
                         AppProperties props) {
        this.permits = permits;
        this.bookings = bookings;
        this.references = references;
        this.notifications = notifications;
        this.props = props;
    }

    private int warningDays() {
        return props.getPermits().getExpiryWarningDays();
    }

    /* ------------------------------------------------------------ Queries */

    @Transactional(readOnly = true)
    public List<PermitView> findAll() {
        int w = warningDays();
        return permits.findAllDetailed().stream().map(p -> PermitView.of(p, w)).toList();
    }

    @Transactional(readOnly = true)
    public PermitView findOne(Long id) {
        return PermitView.of(permits.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Permit", id)), warningDays());
    }

    @Transactional(readOnly = true)
    public List<PermitView> findForBooking(Long bookingId) {
        int w = warningDays();
        return permits.findForBooking(bookingId).stream().map(p -> PermitView.of(p, w)).toList();
    }

    /** Permits whose validity lapses inside the configured window. */
    @Transactional(readOnly = true)
    public List<PermitView> findExpiring(Integer days) {
        int window = days == null ? warningDays() : days;
        int w = warningDays();
        return permits.findExpiringOnOrBefore(LocalDate.now().plusDays(window)).stream()
                .map(p -> PermitView.of(p, w))
                .toList();
    }

    /** Everything the permits screen needs in one call. */
    @Transactional(readOnly = true)
    public PermitDashboardView dashboard() {
        int w = warningDays();
        List<PermitView> all = permits.findAllDetailed().stream().map(p -> PermitView.of(p, w)).toList();

        return new PermitDashboardView(
                w,
                all,
                all.stream().filter(PermitView::expiringSoon).toList(),
                all.stream().filter(PermitView::atRisk).toList(),
                awaitingRequest());
    }

    /** Active bookings that do not yet have a permit that could cover them. */
    @Transactional(readOnly = true)
    public List<UnpermittedBookingView> awaitingRequest() {
        Set<Long> covered = permits.findAllDetailed().stream()
                .filter(p -> p.getStatus() != PermitStatus.REJECTED)
                .map(p -> p.getBooking().getId())
                .collect(java.util.stream.Collectors.toSet());

        LocalDate today = LocalDate.now();

        return bookings.findAllDetailed().stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> !b.getTripDate().isBefore(today))
                .filter(b -> !covered.contains(b.getId()))
                .map(b -> {
                    long lead = ChronoUnit.DAYS.between(today, b.getTripDate());
                    return new UnpermittedBookingView(
                            b.getId(), b.getBookingReference(), b.getCustomer().getFullName(),
                            b.getSafariPackage().getName(),
                            b.getSafariPackage().getPark().getId(),
                            b.getSafariPackage().getPark().getName(),
                            b.getSafariPackage().getPark().getPermitAuthority(),
                            b.getTripDate(),
                            b.getTripDate().plusDays(Math.max(0, b.getSafariPackage().getDurationDays() - 1)),
                            b.getParticipants(),
                            estimateFee(b),
                            lead,
                            lead <= warningDays());
                })
                .sorted(java.util.Comparator.comparing(UnpermittedBookingView::tripDate))
                .toList();
    }

    /* ------------------------------------------------------------ Request */

    @Transactional
    public PermitView request(PermitRequest request, User actor) {
        Booking booking = bookings.findDetailById(request.bookingId())
                .orElseThrow(() -> ApiException.notFound("Booking", request.bookingId()));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("Booking " + booking.getBookingReference()
                    + " is cancelled — no permit is needed.");
        }

        boolean alreadyLive = permits.findForBooking(booking.getId()).stream()
                .anyMatch(p -> p.getStatus() == PermitStatus.PENDING || p.getStatus() == PermitStatus.APPROVED);
        if (alreadyLive) {
            throw ApiException.conflict("Booking " + booking.getBookingReference()
                    + " already has a pending or approved permit. Renew that one instead.");
        }

        LocalDate tripEnd = booking.getTripDate()
                .plusDays(Math.max(0, booking.getSafariPackage().getDurationDays() - 1));
        LocalDate expiry = request.expiryDate() != null
                ? request.expiryDate()
                : tripEnd.plusDays(DEFAULT_GRACE_DAYS);

        if (expiry.isBefore(booking.getTripDate())) {
            throw ApiException.badRequest("A permit cannot expire before the trip starts on "
                    + booking.getTripDate() + ".");
        }

        Permit permit = new Permit();
        permit.setPermitNumber(references.permit());
        permit.setBooking(booking);
        permit.setPark(booking.getSafariPackage().getPark());
        permit.setStatus(PermitStatus.PENDING);
        permit.setExpiryDate(expiry);
        permit.setFeeAmount(estimateFee(booking));
        permit.setCoveredParticipants(booking.getParticipants());
        permit.setNotes(request.notes());
        permit.setRequestedBy(actor);

        Permit saved = permits.save(permit);
        return PermitView.of(permits.findDetailById(saved.getId()).orElse(saved), warningDays());
    }

    /* ----------------------------------------------------------- Decision */

    @Transactional
    public PermitView approve(Long id, PermitDecisionRequest request) {
        Permit permit = permits.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Permit", id));

        if (permit.getStatus() == PermitStatus.APPROVED) {
            throw ApiException.badRequest("This permit is already approved.");
        }

        if (request != null && request.expiryDate() != null) {
            permit.setExpiryDate(request.expiryDate());
        }
        if (permit.getExpiryDate().isBefore(permit.getBooking().getTripDate())) {
            throw ApiException.badRequest("The expiry date is before the trip starts.");
        }

        permit.setStatus(PermitStatus.APPROVED);
        permit.setIssueDate(LocalDate.now());
        permit.setApprovedAt(Instant.now());
        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            permit.setNotes(request.notes());
        }

        Permit saved = permits.save(permit);

        notifications.email(saved.getBooking().getCustomer(),
                "Park permit approved for " + saved.getBooking().getBookingReference(),
                "Your " + saved.getPark().getName() + " permit " + saved.getPermitNumber()
                        + " is approved and valid until " + saved.getExpiryDate() + ".",
                "Permit", saved.getId());

        return PermitView.of(permits.findDetailById(saved.getId()).orElse(saved), warningDays());
    }

    @Transactional
    public PermitView reject(Long id, PermitDecisionRequest request) {
        Permit permit = permits.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Permit", id));
        permit.setStatus(PermitStatus.REJECTED);
        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            permit.setNotes(request.notes());
        }
        Permit saved = permits.save(permit);
        return PermitView.of(permits.findDetailById(saved.getId()).orElse(saved), warningDays());
    }

    /* ------------------------------------------------------------- Renew */

    @Transactional
    public PermitView renew(Long id, PermitRenewRequest request) {
        Permit permit = permits.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Permit", id));

        if (permit.getStatus() == PermitStatus.REJECTED) {
            throw ApiException.badRequest("A rejected permit cannot be renewed — raise a new request.");
        }
        if (!request.expiryDate().isAfter(permit.getExpiryDate())) {
            throw ApiException.badRequest("The new expiry date must be later than the current one ("
                    + permit.getExpiryDate() + ").");
        }

        permit.setExpiryDate(request.expiryDate());
        permit.setStatus(PermitStatus.APPROVED);
        permit.setIssueDate(LocalDate.now());
        permit.setApprovedAt(Instant.now());
        permit.setRenewalCount(permit.getRenewalCount() + 1);
        if (request.notes() != null && !request.notes().isBlank()) {
            permit.setNotes(request.notes());
        }

        Permit saved = permits.save(permit);

        notifications.email(saved.getBooking().getCustomer(),
                "Park permit renewed for " + saved.getBooking().getBookingReference(),
                "Permit " + saved.getPermitNumber() + " has been renewed and is now valid until "
                        + saved.getExpiryDate() + ".",
                "Permit", saved.getId());

        return PermitView.of(permits.findDetailById(saved.getId()).orElse(saved), warningDays());
    }

    /* -------------------------------------------------------------- Edit */

    /** Edits a permit that is still with the park authority. The fee follows the travellers covered. */
    @Transactional
    public PermitView update(Long id, PermitUpdateRequest request) {
        Permit permit = permits.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Permit", id));

        if (permit.getStatus() != PermitStatus.PENDING) {
            throw ApiException.badRequest("Only pending permits can be edited. Use Renew to extend an approved"
                    + " permit, or raise a new request for a rejected one.");
        }

        Booking booking = permit.getBooking();
        if (request.expiryDate().isBefore(booking.getTripDate())) {
            throw ApiException.badRequest("A permit cannot expire before the trip starts on "
                    + booking.getTripDate() + ".");
        }
        if (request.coveredParticipants() > booking.getParticipants()) {
            throw ApiException.badRequest("Booking " + booking.getBookingReference() + " has only "
                    + booking.getParticipants() + " traveller(s), so a permit cannot cover more.");
        }

        permit.setExpiryDate(request.expiryDate());
        permit.setCoveredParticipants(request.coveredParticipants());
        permit.setFeeAmount(permit.getPark().getEntryFeePerPerson()
                .multiply(BigDecimal.valueOf(request.coveredParticipants())));
        permit.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());

        Permit saved = permits.save(permit);
        return PermitView.of(permits.findDetailById(saved.getId()).orElse(saved), warningDays());
    }

    /* ------------------------------------------------------------ Delete */

    /**
     * Pending, rejected and lapsed permits can be deleted. An approved permit that is still valid
     * must be rejected first, so a live trip can't silently lose its cover.
     */
    @Transactional
    public void delete(Long id) {
        Permit permit = permits.findById(id).orElseThrow(() -> ApiException.notFound("Permit", id));

        boolean lapsed = permit.getExpiryDate().isBefore(LocalDate.now());
        boolean deletable = permit.getStatus() == PermitStatus.PENDING
                || permit.getStatus() == PermitStatus.REJECTED
                || permit.getStatus() == PermitStatus.EXPIRED
                || lapsed;
        if (!deletable) {
            throw ApiException.conflict("Permit " + permit.getPermitNumber() + " is approved and valid until "
                    + permit.getExpiryDate() + ". Reject it first if the trip no longer needs it.");
        }
        permits.delete(permit);
    }

    private BigDecimal estimateFee(Booking booking) {
        return booking.getSafariPackage().getPark().getEntryFeePerPerson()
                .multiply(BigDecimal.valueOf(booking.getParticipants()));
    }
}
