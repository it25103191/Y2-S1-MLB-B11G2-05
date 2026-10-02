package com.safari.tms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
            List<UtilisationView> vehicleUtilisation) {
    }

    private ReportDtos() {
    }
}
