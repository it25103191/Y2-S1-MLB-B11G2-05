package com.safari.tms.service.kpi;

import com.safari.tms.domain.Booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Concrete product: the average total price of the bookings that were not cancelled. */
class AverageBookingValueCalculator extends DepartureKpiCalculator {

    @Override
    protected BigDecimal calculate(List<Booking> departing, List<Booking> active) {
        if (active.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return active.stream().map(Booking::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(active.size()), 2, RoundingMode.HALF_UP);
    }
}
