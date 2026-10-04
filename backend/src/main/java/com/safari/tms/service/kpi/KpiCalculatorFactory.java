package com.safari.tms.service.kpi;

import com.safari.tms.domain.enums.KpiMetric;

/**
 * Factory pattern: the factory. Given a KPI metric, creates the calculator for it.
 *
 * <p>Callers such as ReportService and KpiTargetService only know the {@link KpiCalculator}
 * interface; which class does the work is decided here, in one place. A new metric means one new
 * calculator class and one new line in this factory.
 */
public final class KpiCalculatorFactory {

    private KpiCalculatorFactory() {
    }

    public static KpiCalculator create(KpiMetric metric) {
        return switch (metric) {
            case BOOKINGS -> new BookingsCalculator();
            case TRAVELLERS -> new TravellersCalculator();
            case REVENUE -> new RevenueCalculator();
            case CANCELLATION_RATE -> new CancellationRateCalculator();
            case AVERAGE_BOOKING_VALUE -> new AverageBookingValueCalculator();
        };
    }
}

