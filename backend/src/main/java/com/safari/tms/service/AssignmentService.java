package com.safari.tms.service;

import com.safari.tms.domain.*;
import com.safari.tms.domain.enums.AssignmentStatus;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.GuideStatus;
import com.safari.tms.domain.enums.VehicleStatus;
import com.safari.tms.dto.ResourceDtos.*;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.*;
import com.safari.tms.service.crew.CrewSelectionStrategy;
import com.safari.tms.service.crew.LeastBusyCrew;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final BookingRepository bookings;
    private final GuideRepository guides;
    private final VehicleRepository vehicles;
    private final NotificationService notifications;
    private final List<CrewSelectionStrategy> crewStrategies;

    public AssignmentService(AssignmentRepository assignments,
                             BookingRepository bookings,
                             GuideRepository guides,
                             VehicleRepository vehicles,
                             NotificationService notifications,
                             List<CrewSelectionStrategy> crewStrategies) {
        this.assignments = assignments;
        this.bookings = bookings;
        this.guides = guides;
        this.vehicles = vehicles;
        this.notifications = notifications;
        this.crewStrategies = crewStrategies;
    }

    /* ------------------------------------------------------------ Queries */

    @Transactional(readOnly = true)
    public List<AssignmentView> findAll() {
        return assignments.findAllDetailed().stream().map(AssignmentView::of).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentView findOne(Long id) {
        return AssignmentView.of(assignments.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Assignment", id)));
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> findInRange(LocalDate from, LocalDate to) {
        return assignments.findOverlappingRangeDetailed(from, to).stream().map(AssignmentView::of).toList();
    }

    /* ------------------------------------------------------------- Create */

    @Transactional
    public AssignmentView create(AssignmentRequest request, User actor) {
        Booking booking = bookings.findDetailById(request.bookingId())
                .orElseThrow(() -> ApiException.notFound("Booking", request.bookingId()));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("Booking " + booking.getBookingReference()
                    + " is cancelled and cannot be crewed.");
        }

        assignments.findByBookingId(booking.getId()).ifPresent(existing -> {
            if (existing.getStatus() != AssignmentStatus.CANCELLED) {
                throw ApiException.conflict("Booking " + booking.getBookingReference()
                        + " already has an assignment. Edit that one instead.");
            }
        });

        Guide guide = guides.findById(request.guideId())
                .orElseThrow(() -> ApiException.notFound("Guide", request.guideId()));
        Vehicle vehicle = vehicles.findById(request.vehicleId())
                .orElseThrow(() -> ApiException.notFound("Vehicle", request.vehicleId()));

        LocalDate from = booking.getTripDate();
        int days = booking.getSafariPackage().getDurationDays();
        LocalDate to = from.plusDays(Math.max(0, days - 1));

        validate(booking, guide, vehicle, from, to, null, request.override(), request.overrideReason());

        Assignment a = new Assignment();
        a.setBooking(booking);
        a.setGuide(guide);
        a.setVehicle(vehicle);
        a.setAssignmentDate(from);
        a.setDurationDays(days);
        a.syncEndDate();
        a.setStatus(AssignmentStatus.SCHEDULED);
        a.setNotes(request.notes());
        a.setManualOverride(request.override());
        a.setOverrideReason(request.override() ? request.overrideReason() : null);
        a.setAssignedBy(actor);

        Assignment saved = assignments.save(a);

        notifications.email(booking.getCustomer(),
                "Your crew for " + booking.getBookingReference(),
                "Good news — " + guide.getFullName() + " will guide your "
                        + booking.getSafariPackage().getName() + " departure on " + from
                        + ", travelling in " + vehicle.getModel() + " (" + vehicle.getRegistrationNumber() + ").",
                "Assignment", saved.getId());

        return AssignmentView.of(assignments.findDetailById(saved.getId()).orElse(saved));
    }

    /* ------------------------------------------------------------- Update */

    @Transactional
    public AssignmentView update(Long id, AssignmentRequest request, User actor) {
        Assignment a = assignments.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Assignment", id));

        Guide guide = guides.findById(request.guideId())
                .orElseThrow(() -> ApiException.notFound("Guide", request.guideId()));
        Vehicle vehicle = vehicles.findById(request.vehicleId())
                .orElseThrow(() -> ApiException.notFound("Vehicle", request.vehicleId()));

        LocalDate from = a.getAssignmentDate();
        LocalDate to = a.getEndDate();

        validate(a.getBooking(), guide, vehicle, from, to, a.getId(), request.override(), request.overrideReason());

        a.setGuide(guide);
        a.setVehicle(vehicle);
        a.setNotes(request.notes());
        a.setManualOverride(request.override());
        a.setOverrideReason(request.override() ? request.overrideReason() : null);
        a.setAssignedBy(actor);

        Assignment saved = assignments.save(a);
        return AssignmentView.of(assignments.findDetailById(saved.getId()).orElse(saved));
    }

    @Transactional
    public AssignmentView changeStatus(Long id, AssignmentStatus status) {
        Assignment a = assignments.findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("Assignment", id));
        a.setStatus(status);
        return AssignmentView.of(assignments.save(a));
    }

    @Transactional
    public void delete(Long id) {
        Assignment a = assignments.findById(id)
                .orElseThrow(() -> ApiException.notFound("Assignment", id));
        assignments.delete(a);
    }

    /* --------------------------------------------------------- Validation */

    /**
     * Rejects an assignment that would double-book a guide or vehicle, or that uses a resource
     * which is off the road / off duty. A manual override with a reason bypasses these checks and
     * is recorded on the assignment.
     */
    private void validate(Booking booking, Guide guide, Vehicle vehicle,
                          LocalDate from, LocalDate to, Long excludeId,
                          boolean override, String overrideReason) {

        List<ClashView> clashes = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        assignments.findGuideConflicts(guide.getId(), from, to, excludeId).forEach(c ->
                clashes.add(new ClashView("GUIDE", guide.getId(), guide.getFullName(), c.getId(),
                        c.getBooking().getBookingReference(), c.getAssignmentDate(), c.getEndDate())));

        assignments.findVehicleConflicts(vehicle.getId(), from, to, excludeId).forEach(c ->
                clashes.add(new ClashView("VEHICLE", vehicle.getId(), vehicle.getRegistrationNumber(), c.getId(),
                        c.getBooking().getBookingReference(), c.getAssignmentDate(), c.getEndDate())));

        if (guide.getStatus() != GuideStatus.AVAILABLE) {
            blockers.add(guide.getFullName() + " is marked "
                    + guide.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            blockers.add(vehicle.getRegistrationNumber() + " is marked "
                    + vehicle.getStatus().name().toLowerCase() + ".");
        }
        if (vehicle.getCapacity() < booking.getParticipants()) {
            blockers.add(vehicle.getRegistrationNumber() + " seats " + vehicle.getCapacity()
                    + " but this trip carries " + booking.getParticipants() + " travellers.");
        }

        if (clashes.isEmpty() && blockers.isEmpty()) {
            return;
        }

        if (override) {
            if (overrideReason == null || overrideReason.isBlank()) {
                throw ApiException.badRequest("A manual override needs a reason for the audit trail.");
            }
            return;
        }

        StringBuilder message = new StringBuilder();
        if (!clashes.isEmpty()) {
            ClashView first = clashes.get(0);
            message.append(first.resourceType().equals("GUIDE") ? "Guide " : "Vehicle ")
                    .append(first.resourceName())
                    .append(" is already committed to ")
                    .append(first.bookingReference())
                    .append(" from ").append(first.from()).append(" to ").append(first.to())
                    .append('.');
            if (clashes.size() > 1) {
                message.append(" (").append(clashes.size()).append(" clashes in total.)");
            }
        }
        if (!blockers.isEmpty()) {
            if (message.length() > 0) message.append(' ');
            message.append(String.join(" ", blockers));
        }

        throw ApiException.conflict(message.toString(), Map.of(
                "clashes", clashes,
                "blockers", blockers,
                "windowFrom", from.toString(),
                "windowTo", to.toString(),
                "canOverride", true));
    }

    /* --------------------------------------------------------- Suggestion */

    /**
     * Lists every guide and vehicle for a booking, marks which are free, and recommends a pairing.
     *
     * <p>Strategy pattern: this method is the context. It does the work every rule needs (finding
     * clashes, statuses and workload), then lets the chosen {@link CrewSelectionStrategy} pick.
     *
     * @param strategyKey which rule to use; {@code null} means the default, least busy
     */
    @Transactional(readOnly = true)
    public SuggestionView suggest(Long bookingId, String strategyKey) {
        CrewSelectionStrategy strategy = crewStrategy(strategyKey);
        Booking booking = bookings.findDetailById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking", bookingId));

        LocalDate from = booking.getTripDate();
        int days = booking.getSafariPackage().getDurationDays();
        LocalDate to = from.plusDays(Math.max(0, days - 1));

        Assignment existing = assignments.findByBookingId(bookingId).orElse(null);
        Long excludeId = existing == null ? null : existing.getId();

        Set<Long> busyGuides = new HashSet<>(assignments.findBusyGuideIds(from, to, excludeId));
        Set<Long> busyVehicles = new HashSet<>(assignments.findBusyVehicleIds(from, to, excludeId));

        LocalDate windowStart = LocalDate.now();
        LocalDate windowEnd = windowStart.plusYears(1);

        List<CandidateView> guideCandidates = guides.findAllByOrderByFullNameAsc().stream()
                .map(g -> {
                    boolean busy = busyGuides.contains(g.getId());
                    boolean offDuty = g.getStatus() != GuideStatus.AVAILABLE;
                    String reason = busy
                            ? "Already crewing another trip in this window"
                            : offDuty ? "Marked " + g.getStatus().name().toLowerCase().replace('_', ' ') : null;
                    return new CandidateView(
                            g.getId(),
                            g.getFullName(),
                            (g.getSpecialization() == null ? "Guide" : g.getSpecialization())
                                    + " · " + g.getYearsExperience() + " yrs"
                                    + (g.getLanguages() == null ? "" : " · " + g.getLanguages()),
                            g.getStatus().name(),
                            !busy && !offDuty,
                            reason,
                            null,
                            assignments.countGuideAssignments(g.getId(), windowStart, windowEnd),
                            g.getYearsExperience());
                })
                .toList();

        List<CandidateView> vehicleCandidates = vehicles.findAllByOrderByRegistrationNumberAsc().stream()
                .map(v -> {
                    boolean busy = busyVehicles.contains(v.getId());
                    boolean offRoad = v.getStatus() != VehicleStatus.AVAILABLE;
                    boolean tooSmall = v.getCapacity() < booking.getParticipants();
                    String reason = busy
                            ? "Already out on another trip in this window"
                            : offRoad ? "Marked " + v.getStatus().name().toLowerCase()
                            : tooSmall ? "Seats " + v.getCapacity() + ", needs " + booking.getParticipants()
                            : null;
                    return new CandidateView(
                            v.getId(),
                            v.getRegistrationNumber(),
                            v.getModel() + " · " + v.getType() + " · " + v.getCapacity() + " seats",
                            v.getStatus().name(),
                            !busy && !offRoad && !tooSmall,
                            reason,
                            v.getCapacity(),
                            assignments.countVehicleAssignments(v.getId(), windowStart, windowEnd),
                            null);
                })
                .toList();

        // The chosen strategy decides who to recommend from the free candidates.
        Long bestGuide = strategy.pickGuide(guideCandidates);
        Long bestVehicle = strategy.pickVehicle(vehicleCandidates, booking.getParticipants());

        String rationale;
        if (bestGuide == null && bestVehicle == null) {
            rationale = "No guide or vehicle is free for " + from + " to " + to + ". Reschedule or override manually.";
        } else if (bestGuide == null) {
            rationale = "No guide is free for this window — every guide is either committed or off duty.";
        } else if (bestVehicle == null) {
            rationale = "No vehicle is free with at least " + booking.getParticipants() + " seats for this window.";
        } else {
            rationale = strategy.rationale(booking.getParticipants());
        }

        return new SuggestionView(booking.getId(), booking.getBookingReference(), from, to,
                booking.getParticipants(), guideCandidates, vehicleCandidates,
                bestGuide, bestVehicle, rationale, strategy.key());
    }

    /** Picks the crew-selection strategy by key; unknown keys are refused. */
    private CrewSelectionStrategy crewStrategy(String key) {
        String wanted = key == null || key.isBlank() ? LeastBusyCrew.KEY : key.trim();
        return crewStrategies.stream()
                .filter(s -> s.key().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown suggestion rule '" + wanted + "'. Use one of: "
                        + String.join(", ", crewStrategies.stream().map(CrewSelectionStrategy::key).toList()) + "."));
    }
}
