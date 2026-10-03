package com.safari.tms.service.kpi;

import com.safari.tms.domain.Booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Concrete product: the percentage of departures in the months that were cancelled. */
class CancellationRateCalculator extends DepartureKpiCalculator {

    @Override
    protected BigDecimal calculate(List<Booking> departing, List<Booking> active) {
        if (departing.isEmpty()) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(100.0 * (departing.size() - active.size()) / departing.size())
                .setScale(1, RoundingMode.HALF_UP);
    }
}
