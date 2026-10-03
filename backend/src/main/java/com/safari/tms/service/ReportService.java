package com.safari.tms.service;

import com.safari.tms.domain.*;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.domain.enums.ComplaintStatus;
import com.safari.tms.domain.enums.KpiMetric;
import com.safari.tms.domain.enums.PaymentStatus;
import com.safari.tms.dto.ReportDtos.*;
import com.safari.tms.repo.*;
import com.safari.tms.service.kpi.KpiCalculatorFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only aggregations for the analytics dashboard.
 *
 * <p>Every endpoint returns chart-ready shapes ({@code {label, value}}) rather than entity dumps,
 * so the front end can bind straight to Recharts without reshaping.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final int TOP_N = 6;

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final ComplaintRepository complaints;
    private final AssignmentRepository assignments;
    private final GuideRepository guides;
    private final VehicleRepository vehicles;
    private final KpiTargetRepository kpiTargets;

    public ReportService(BookingRepository bookings,
                         PaymentRepository payments,
                         ComplaintRepository complaints,
                         AssignmentRepository assignments,
                         GuideRepository guides,
                         VehicleRepository vehicles,
                         KpiTargetRepository kpiTargets) {
        this.bookings = bookings;
        this.payments = payments;
        this.complaints = complaints;
        this.assignments = assignments;
        this.guides = guides;
        this.vehicles = vehicles;
        this.kpiTargets = kpiTargets;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(LocalDate from, LocalDate to) {
        LocalDate requestedFrom = from != null ? from : LocalDate.now().minusMonths(6).withDayOfMonth(1);
        LocalDate requestedTo = to != null ? to : LocalDate.now().plusMonths(6);

        // Tolerate a reversed range rather than returning nothing.
        final LocalDate start = requestedTo.isBefore(requestedFrom) ? requestedTo : requestedFrom;
        final LocalDate end = requestedTo.isBefore(requestedFrom) ? requestedFrom : requestedTo;

        List<Booking> inRange = bookings.findAllDetailed().stream()
                .filter(b -> !b.getTripDate().isBefore(start) && !b.getTripDate().isAfter(end))
                .toList();

        List<KpiTarget> targets = targetsIn(start, end);

        return new DashboardView(
                start, end,
                kpis(inRange, start, end),
                bookingsOverTime(inRange, start, end),
                revenueTrend(start, end),
                popularPackages(inRange),
                popularParks(inRange),
                bookingStatusBreakdown(inRange),
                paymentStatusBreakdown(start, end),
                guideUtilisation(start, end),
                vehicleUtilisation(start, end),
                targetProgress(start, end, targets),
                targets.stream()
                        .map(t -> new MonthlyTargetView(YearMonth.from(t.getPeriodMonth()).toString(),
                                t.getMetric(), t.getTargetValue()))
                        .toList());
    }

    /* -------------------------------------------------------- KPI actuals */

    /** One read of the raw data, so several metrics can be computed without re-querying. */
    public record Snapshot(List<Booking> bookings, List<Payment> payments) {
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        return new Snapshot(bookings.findAllDetailed(), payments.findAllDetailed());
    }

    /**
     * The actual value of a metric over a set of months, using the dashboard's own definitions:
     * bookings, travellers, cancellation rate and average value count departures in those months;
     * revenue counts settled payments received in those months.
     */
    public BigDecimal actual(KpiMetric metric, Collection<YearMonth> months, Snapshot data) {
        // Factory pattern: the factory picks the calculator class for this metric.
        return KpiCalculatorFactory.create(metric).actual(months, data);
    }

    /** {@code actual} as a percentage of {@code target}, or {@code null} when the target is zero. */
    public static BigDecimal percentOf(BigDecimal actual, BigDecimal target) {
        if (target.signum() == 0) {
            return null;
        }
        return actual.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
    }

    private List<KpiTarget> targetsIn(LocalDate start, LocalDate end) {
        return kpiTargets.findByPeriodMonthBetween(YearMonth.from(start).atDay(1), YearMonth.from(end).atDay(1));
    }

    /**
     * Actual against target per metric. Only months that actually have a target count, so a single
     * September target is never compared with a whole year of revenue. Counts and money add up
     * across months; rates and averages are averaged.
     */
    private List<KpiProgressView> targetProgress(LocalDate start, LocalDate end, List<KpiTarget> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        Snapshot data = snapshot();
        int monthsInRange = monthsBetween(start, end).size();

        Map<KpiMetric, List<KpiTarget>> byMetric = new EnumMap<>(KpiMetric.class);
        targets.forEach(t -> byMetric.computeIfAbsent(t.getMetric(), m -> new ArrayList<>()).add(t));

        List<KpiProgressView> progress = new ArrayList<>();
        byMetric.forEach((metric, list) -> {
            List<YearMonth> months = list.stream().map(t -> YearMonth.from(t.getPeriodMonth())).toList();
            BigDecimal sum = list.stream().map(KpiTarget::getTargetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = metric.isAdditive()
                    ? sum
                    : sum.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
            BigDecimal actual = actual(metric, months, data);

            progress.add(new KpiProgressView(metric, metric.getLabel(), metric.getUnit(), metric.isHigherIsBetter(),
                    target, actual, percentOf(actual, target), metric.isMet(actual, target),
                    list.size(), monthsInRange));
        });
        return progress;
    }

    /* --------------------------------------------------------------- KPIs */

    private KpiView kpis(List<Booking> inRange, LocalDate start, LocalDate end) {
        long total = inRange.size();
        long cancelled = inRange.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
        long confirmed = inRange.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED
                        || b.getStatus() == BookingStatus.COMPLETED).count();

        BigDecimal revenue = payments.findAllDetailed().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS && p.getPaidAt() != null)
                .filter(p -> withinRange(p.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate(), start, end))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossValue = inRange.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .map(Booking::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeBookings = inRange.stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED).count();

        BigDecimal avgValue = activeBookings == 0
                ? BigDecimal.ZERO
                : grossValue.divide(BigDecimal.valueOf(activeBookings), 2, RoundingMode.HALF_UP);

        BigDecimal cancellationRate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelled * 100.0 / total).setScale(1, RoundingMode.HALF_UP);

        BigDecimal outstanding = inRange.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .map(Booking::getBalanceDue)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long travellers = inRange.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .mapToLong(Booking::getParticipants).sum();

        // Complaints and upcoming trips describe "right now", not the selected window.
        long openCases = complaints.countByStatusIn(
                List.of(ComplaintStatus.OPEN, ComplaintStatus.IN_PROGRESS));

        long upcoming = bookings.countUpcoming(LocalDate.now(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));

        return new KpiView(total, revenue, openCases, upcoming, confirmed, cancelled,
                cancellationRate, avgValue, outstanding, travellers);
    }

    /* ------------------------------------------------------------- Series */

    private List<ChartPoint> bookingsOverTime(List<Booking> inRange, LocalDate start, LocalDate end) {
        Map<YearMonth, List<Booking>> byMonth = inRange.stream()
                .collect(Collectors.groupingBy(b -> YearMonth.from(b.getTripDate())));

        return monthsBetween(start, end).stream()
                .map(ym -> {
                    List<Booking> month = byMonth.getOrDefault(ym, List.of());
                    long confirmed = month.stream()
                            .filter(b -> b.getStatus() != BookingStatus.CANCELLED).count();
                    long cancelled = month.stream()
                            .filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();
                    return new ChartPoint(ym.format(MONTH_LABEL), BigDecimal.valueOf(confirmed),
                            BigDecimal.valueOf(cancelled), ym.toString());
                })
                .toList();
    }

    private List<ChartPoint> revenueTrend(LocalDate start, LocalDate end) {
        Map<YearMonth, BigDecimal> byMonth = payments.findAllDetailed().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS && p.getPaidAt() != null)
                .filter(p -> withinRange(p.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate(), start, end))
                .collect(Collectors.groupingBy(
                        p -> YearMonth.from(p.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate()),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        return monthsBetween(start, end).stream()
                .map(ym -> new ChartPoint(ym.format(MONTH_LABEL),
                        byMonth.getOrDefault(ym, BigDecimal.ZERO), null, ym.toString()))
                .toList();
    }

    private List<ChartPoint> popularPackages(List<Booking> inRange) {
        Map<String, List<Booking>> grouped = inRange.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(b -> b.getSafariPackage().getName()));

        return grouped.entrySet().stream()
                .map(e -> new ChartPoint(e.getKey(),
                        BigDecimal.valueOf(e.getValue().size()),
                        e.getValue().stream().map(Booking::getTotalPrice)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getKey()))
                .sorted(Comparator.comparing(ChartPoint::value).reversed())
                .limit(TOP_N)
                .toList();
    }

    private List<ChartPoint> popularParks(List<Booking> inRange) {
        Map<String, List<Booking>> grouped = inRange.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(b -> b.getSafariPackage().getPark().getName()));

        return grouped.entrySet().stream()
                .map(e -> new ChartPoint(e.getKey(),
                        BigDecimal.valueOf(e.getValue().size()),
                        BigDecimal.valueOf(e.getValue().stream()
                                .mapToLong(Booking::getParticipants).sum()),
                        e.getKey()))
                .sorted(Comparator.comparing(ChartPoint::value).reversed())
                .toList();
    }

    private List<ChartPoint> bookingStatusBreakdown(List<Booking> inRange) {
        return Arrays.stream(BookingStatus.values())
                .map(s -> ChartPoint.of(label(s.name()),
                        inRange.stream().filter(b -> b.getStatus() == s).count()))
                .filter(p -> p.value().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private List<ChartPoint> paymentStatusBreakdown(LocalDate start, LocalDate end) {
        List<Payment> inRange = payments.findAllDetailed().stream()
                .filter(p -> withinRange(p.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), start, end))
                .toList();

        return Arrays.stream(PaymentStatus.values())
                .map(s -> new ChartPoint(label(s.name()),
                        BigDecimal.valueOf(inRange.stream().filter(p -> p.getStatus() == s).count()),
                        inRange.stream().filter(p -> p.getStatus() == s)
                                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        s.name()))
                .filter(p -> p.value().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    /* -------------------------------------------------------- Utilisation */

    private List<UtilisationView> guideUtilisation(LocalDate start, LocalDate end) {
        long windowDays = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        List<Assignment> inRange = assignments.findOverlappingRangeDetailed(start, end);

        return guides.findAllByOrderByFullNameAsc().stream()
                .map(g -> {
                    List<Assignment> theirs = inRange.stream()
                            .filter(a -> a.getGuide().getId().equals(g.getId()))
                            .toList();
                    long days = theirs.stream().mapToLong(a -> deployedDays(a, start, end)).sum();
                    return new UtilisationView(
                            g.getFullName(),
                            (g.getSpecialization() == null ? "Guide" : g.getSpecialization())
                                    + " · " + g.getStatus().name().toLowerCase().replace('_', ' '),
                            theirs.size(), days, percent(days, windowDays));
                })
                .sorted(Comparator.comparing(UtilisationView::daysDeployed).reversed())
                .toList();
    }

    private List<UtilisationView> vehicleUtilisation(LocalDate start, LocalDate end) {
        long windowDays = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        List<Assignment> inRange = assignments.findOverlappingRangeDetailed(start, end);

        return vehicles.findAllByOrderByRegistrationNumberAsc().stream()
                .map(v -> {
                    List<Assignment> theirs = inRange.stream()
                            .filter(a -> a.getVehicle().getId().equals(v.getId()))
                            .toList();
                    long days = theirs.stream().mapToLong(a -> deployedDays(a, start, end)).sum();
                    return new UtilisationView(
                            v.getRegistrationNumber(),
                            v.getModel() + " · " + v.getCapacity() + " seats · "
                                    + v.getStatus().name().toLowerCase(),
                            theirs.size(), days, percent(days, windowDays));
                })
                .sorted(Comparator.comparing(UtilisationView::daysDeployed).reversed())
                .toList();
    }

    /* ------------------------------------------------------------ Helpers */

    /** Days of an assignment that actually fall inside the reporting window. */
    private long deployedDays(Assignment a, LocalDate start, LocalDate end) {
        LocalDate from = a.getAssignmentDate().isBefore(start) ? start : a.getAssignmentDate();
        LocalDate to = a.getEndDate().isAfter(end) ? end : a.getEndDate();
        return to.isBefore(from) ? 0 : ChronoUnit.DAYS.between(from, to) + 1;
    }

    private BigDecimal percent(long part, long whole) {
        if (whole <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    private boolean withinRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private List<YearMonth> monthsBetween(LocalDate start, LocalDate end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        // Guard against absurd ranges producing thousands of buckets.
        int guard = 0;
        while (!cursor.isAfter(last) && guard++ < 120) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private String label(String enumName) {
        String lower = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
