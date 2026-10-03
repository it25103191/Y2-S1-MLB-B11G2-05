package com.safari.tms.domain;

import com.safari.tms.domain.enums.KpiMetric;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A monthly goal for one metric, e.g. "Revenue in September 2026: 30,000". */
@Entity
@Table(name = "kpi_targets",
        uniqueConstraints = @UniqueConstraint(name = "uk_kpi_targets_metric_month",
                columnNames = {"metric", "periodMonth"}))
@Getter
@Setter
@NoArgsConstructor
public class KpiTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private KpiMetric metric;

    /** Always the first day of the month the target applies to. */
    @Column(nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal targetValue;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
