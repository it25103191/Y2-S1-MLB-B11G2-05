package com.safari.tms.domain;

import com.safari.tms.domain.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds",
        uniqueConstraints = @UniqueConstraint(name = "uk_refunds_ref", columnNames = "refundReference"),
        indexes = @Index(name = "ix_refunds_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String refundReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Total amount the customer had actually paid at the time the refund was calculated. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaidAtRequest = BigDecimal.ZERO;

    /** Amount produced by the cancellation-window policy engine. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedAmount = BigDecimal.ZERO;

    /** Amount a finance officer actually approved (may differ from calculated). */
    @Column(precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(nullable = false)
    private Integer refundPercentage = 0;

    @Column(nullable = false, length = 200)
    private String policyApplied;

    @Column(nullable = false)
    private Integer daysBeforeTrip = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(length = 500)
    private String reason;

    @Column(length = 500)
    private String decisionNotes;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    private Instant processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;
}

