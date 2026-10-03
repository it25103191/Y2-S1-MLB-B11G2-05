package com.safari.tms.dto;

import com.safari.tms.domain.enums.KpiMetric;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class ReportDtos {

    /** Chart-ready point. {@code value} is the primary metric; {@code secondary} is optional. */
    public record ChartPoint(String label, BigDecimal value, BigDecimal secondary, String key) {

        public static ChartPoint of(String label, long value) {
            return new ChartPoint(label, BigDecimal.valueOf(value), null, label);
        }

        public static ChartPoint of(String label, BigDecimal value) {
            return new ChartPoint(label, value, null, label);
        }

        public static ChartPoint of(String label, long value, BigDecimal secondary) {
            return new ChartPoint(label, BigDecimal.valueOf(value), secondary, label);
        }
    }

    public record KpiView(
            long totalBookings,
            BigDecimal totalRevenue,
            long activeComplaints,
            long upcomingTrips,
            long confirmedBookings,
            long cancelledBookings,
            BigDecimal cancellationRate,
            BigDecimal averageBookingValue,
            BigDecimal outstandingBalance,
            long totalTravellers) {
    }

    public record UtilisationView(
            String label,
            String detail,
            long assignments,
            long daysDeployed,
            BigDecimal utilisationPercent) {
    }

    /** Everything the analytics dashboard renders, for one date range. */
    public record DashboardView(
            LocalDate from,
            LocalDate to,
            KpiView kpis,
            List<ChartPoint> bookingsOverTime,
            List<ChartPoint> revenueTrend,
            List<ChartPoint> popularPackages,
            List<ChartPoint> popularParks,
            List<ChartPoint> bookingStatusBreakdown,
            List<ChartPoint> paymentStatusBreakdown,
            List<UtilisationView> guideUtilisation,
            List<UtilisationView> vehicleUtilisation,
            List<KpiProgressView> targetProgress,
            List<MonthlyTargetView> monthlyTargets) {
    }

    /* ------------------------------------------------------- KPI targets */

    public record KpiTargetRequest(
            @NotNull(message = "Choose a metric") KpiMetric metric,

            /** Month the target applies to, as {@code yyyy-MM}. */
            @NotNull(message = "Choose a month") YearMonth month,

            @NotNull(message = "Enter a target value")
            @DecimalMin(value = "0.00", message = "Target cannot be negative")
            BigDecimal targetValue,

            @Size(max = 500) String notes) {
    }

    public enum TargetStatus { ACHIEVED, MISSED, IN_PROGRESS, UPCOMING }

    public record KpiTargetView(
            Long id,
            KpiMetric metric,
            String metricLabel,
            KpiMetric.Unit unit,
            boolean higherIsBetter,
            String month,
            String monthLabel,
            BigDecimal targetValue,
            BigDecimal actualValue,
            BigDecimal percentOfTarget,
            boolean met,
            TargetStatus status,
            String notes,
            String createdByName,
            String updatedByName,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Actual against target for one metric, over the months of a report range that have targets. */
    public record KpiProgressView(
            KpiMetric metric,
            String metricLabel,
            KpiMetric.Unit unit,
            boolean higherIsBetter,
            BigDecimal targetValue,
            BigDecimal actualValue,
            BigDecimal percentOfTarget,
            boolean met,
            int monthsWithTarget,
            int monthsInRange) {
    }

    /** One month's target for one metric, keyed like the chart series ({@code yyyy-MM}). */
    public record MonthlyTargetView(String key, KpiMetric metric, BigDecimal targetValue) {
    }

    private ReportDtos() {
    }
}
