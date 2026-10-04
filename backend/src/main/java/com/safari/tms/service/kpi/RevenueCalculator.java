package com.safari.tms.service.kpi;

import com.safari.tms.domain.Payment;
import com.safari.tms.domain.enums.PaymentStatus;
import com.safari.tms.service.ReportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Concrete product: money received in the months. Unlike the other metrics this counts payments by
 * the month they were paid, not bookings by departure month.
 */
class RevenueCalculator implements KpiCalculator {

    @Override
    public BigDecimal actual(Collection<YearMonth> months, ReportService.Snapshot data) {
        Set<YearMonth> wanted = new HashSet<>(months);
        return data.payments().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS && p.getPaidAt() != null)
                .filter(p -> wanted.contains(YearMonth.from(p.getPaidAt().atZone(ZoneOffset.UTC))))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
