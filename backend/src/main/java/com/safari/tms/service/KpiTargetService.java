package com.safari.tms.service;

import com.safari.tms.domain.KpiTarget;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.KpiMetric;
import com.safari.tms.dto.ReportDtos.KpiTargetRequest;
import com.safari.tms.dto.ReportDtos.KpiTargetView;
import com.safari.tms.dto.ReportDtos.TargetStatus;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.KpiTargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Monthly KPI targets. Each view is paired with the live actual so the targets screen shows
 * progress without a second request.
 */
@Service
public class KpiTargetService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final int MAX_YEARS_AWAY = 5;

    private final KpiTargetRepository targets;
    private final ReportService reports;

    public KpiTargetService(KpiTargetRepository targets, ReportService reports) {
        this.targets = targets;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public List<KpiTargetView> findAll() {
        ReportService.Snapshot data = reports.snapshot();
        return targets.findAllDetailed().stream().map(t -> view(t, data)).toList();
    }

    @Transactional(readOnly = true)
    public KpiTargetView findOne(Long id) {
        return view(require(id), reports.snapshot());
    }

    @Transactional
    public KpiTargetView create(KpiTargetRequest request, User actor) {
        validate(request);
        ensureUnique(request.metric(), request.month(), null);

        KpiTarget target = new KpiTarget();
        apply(target, request);
        target.setCreatedBy(actor);

        return view(targets.save(target), reports.snapshot());
    }

    @Transactional
    public KpiTargetView update(Long id, KpiTargetRequest request, User actor) {
        KpiTarget target = require(id);
        validate(request);
        ensureUnique(request.metric(), request.month(), id);

        apply(target, request);
        target.setUpdatedBy(actor);
        target.setUpdatedAt(Instant.now());

        return view(targets.save(target), reports.snapshot());
    }

    @Transactional
    public void delete(Long id) {
        targets.delete(require(id));
    }

    /* ------------------------------------------------------------ helpers */

    private void validate(KpiTargetRequest request) {
        KpiMetric metric = request.metric();
        BigDecimal value = request.targetValue();

        if (metric == KpiMetric.CANCELLATION_RATE) {
            if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw ApiException.badRequest("A cancellation rate target must be between 0 and 100%.");
            }
        } else if (value.signum() <= 0) {
            throw ApiException.badRequest("A " + metric.getLabel().toLowerCase() + " target must be greater than zero.");
        }

        if (metric.getUnit() == KpiMetric.Unit.COUNT && value.stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest("A " + metric.getLabel().toLowerCase() + " target must be a whole number.");
        }

        YearMonth now = YearMonth.now();
        if (request.month().isBefore(now.minusYears(MAX_YEARS_AWAY))
                || request.month().isAfter(now.plusYears(MAX_YEARS_AWAY))) {
            throw ApiException.badRequest("Choose a month within " + MAX_YEARS_AWAY + " years of today.");
        }
    }

    private void ensureUnique(KpiMetric metric, YearMonth month, Long selfId) {
        targets.findByMetricAndPeriodMonth(metric, month.atDay(1))
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("There is already a " + metric.getLabel().toLowerCase()
                            + " target for " + month.format(MONTH_LABEL) + " ("
                            + existing.getTargetValue().stripTrailingZeros().toPlainString()
                            + "). Edit that one instead.");
                });
    }

    private void apply(KpiTarget target, KpiTargetRequest request) {
        target.setMetric(request.metric());
        target.setPeriodMonth(request.month().atDay(1));
        target.setTargetValue(request.targetValue().setScale(2, RoundingMode.HALF_UP));
        target.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
    }

    /**
     * Pairs a target with its actual. Past months are Achieved or Missed, the current month is
     * In progress and later months are Upcoming.
     */
    KpiTargetView view(KpiTarget t, ReportService.Snapshot data) {
        YearMonth month = YearMonth.from(t.getPeriodMonth());
        KpiMetric metric = t.getMetric();
        BigDecimal target = t.getTargetValue();
        BigDecimal actual = reports.actual(metric, List.of(month), data);
        boolean met = metric.isMet(actual, target);

        YearMonth now = YearMonth.now();
        TargetStatus status = month.isAfter(now) ? TargetStatus.UPCOMING
                : month.equals(now) ? TargetStatus.IN_PROGRESS
                : met ? TargetStatus.ACHIEVED : TargetStatus.MISSED;

        return new KpiTargetView(
                t.getId(), metric, metric.getLabel(), metric.getUnit(), metric.isHigherIsBetter(),
                month.toString(), month.format(MONTH_LABEL),
                target, actual, ReportService.percentOf(actual, target), met, status, t.getNotes(),
                t.getCreatedBy() == null ? null : t.getCreatedBy().getFullName(),
                t.getUpdatedBy() == null ? null : t.getUpdatedBy().getFullName(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private KpiTarget require(Long id) {
        return targets.findDetailById(id).orElseThrow(() -> ApiException.notFound("KPI target", id));
    }
}
