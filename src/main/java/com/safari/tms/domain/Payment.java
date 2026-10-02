package com.safari.tms.domain;

import com.safari.tms.domain.enums.PaymentMethod;
import com.safari.tms.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_ref", columnNames = "paymentReference"),
        indexes = @Index(name = "ix_payments_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String paymentReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method = PaymentMethod.CREDIT_CARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(length = 60)
    private String gatewayReference;

    @Column(length = 300)
    private String failureReason;

    @Column(length = 4)
    private String cardLast4;

    @Column(length = 120)
    private String cardHolderName;

    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}

