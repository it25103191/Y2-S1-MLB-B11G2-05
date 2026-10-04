package com.safari.tms.service.kpi;

import com.safari.tms.service.ReportService;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;

/**
 * Factory pattern: the product interface. Works out the actual value of one KPI metric over a set
 * of months. {@link KpiCalculatorFactory} decides which concrete calculator to create.
 */
public interface KpiCalculator {

    BigDecimal actual(Collection<YearMonth> months, ReportService.Snapshot data);
}

