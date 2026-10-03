package com.safari.tms.service.kpi;

import com.safari.tms.domain.Booking;
import com.safari.tms.domain.enums.BookingStatus;
import com.safari.tms.service.ReportService;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared base for the metrics measured on departures: picks the bookings departing in the chosen
 * months, and the ones among them that were not cancelled, then lets the subclass do the sum.
 */
abstract class DepartureKpiCalculator implements KpiCalculator {

    @Override
    public final BigDecimal actual(Collection<YearMonth> months, ReportService.Snapshot data) {
        Set<YearMonth> wanted = new HashSet<>(months);
        List<Booking> departing = data.bookings().stream()
                .filter(b -> wanted.contains(YearMonth.from(b.getTripDate())))
                .toList();
        List<Booking> active = departing.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .toList();
        return calculate(departing, active);
    }

    /**
     * @param departing every booking departing in the months, cancelled or not
     * @param active    the departing bookings that were not cancelled
     */
    protected abstract BigDecimal calculate(List<Booking> departing, List<Booking> active);
}
