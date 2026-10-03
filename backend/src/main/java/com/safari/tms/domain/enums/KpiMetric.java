package com.safari.tms.domain.enums;

import java.math.BigDecimal;

/**
 * Metrics a KPI target can be set against.
 *
 * <p>{@code higherIsBetter} decides what "achieved" means (cancellation rate is the one metric
 * where lower wins). {@code additive} decides how several monthly targets combine when a report
 * spans more than one month: counts and money are summed, rates and averages are averaged.
 */
public enum KpiMetric {

    BOOKINGS("Bookings", Unit.COUNT, true, true),
    REVENUE("Revenue", Unit.CURRENCY, true, true),
    TRAVELLERS("Travellers", Unit.COUNT, true, true),
    CANCELLATION_RATE("Cancellation rate", Unit.PERCENT, false, false),
    AVERAGE_BOOKING_VALUE("Average booking value", Unit.CURRENCY, true, false);

    public enum Unit { COUNT, CURRENCY, PERCENT }

    private final String label;
    private final Unit unit;
    private final boolean higherIsBetter;
    private final boolean additive;

    KpiMetric(String label, Unit unit, boolean higherIsBetter, boolean additive) {
        this.label = label;
        this.unit = unit;
        this.higherIsBetter = higherIsBetter;
        this.additive = additive;
    }

    public String getLabel() {
        return label;
    }

    public Unit getUnit() {
        return unit;
    }

    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    public boolean isAdditive() {
        return additive;
    }

    /** True when {@code actual} satisfies {@code target} in this metric's direction. */
    public boolean isMet(BigDecimal actual, BigDecimal target) {
        int cmp = actual.compareTo(target);
        return higherIsBetter ? cmp >= 0 : cmp <= 0;
    }
}
