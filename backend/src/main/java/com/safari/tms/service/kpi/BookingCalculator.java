package com.safari.tms.service.kpi;

import com.safari.tms.domain.Booking;

import java.math.BigDecimal;
import java.util.List;

/** Concrete product: how many bookings depart in the months (cancelled ones excluded). */
class BookingsCalculator extends DepartureKpiCalculator {

    @Override
    protected BigDecimal calculate(List<Booking> departing, List<Booking> active) {
        return BigDecimal.valueOf(active.size());
    }
}