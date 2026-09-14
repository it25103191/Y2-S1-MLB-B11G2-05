package com.safari.tms.domain;

import com.safari.tms.domain.enums.PermitStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "permits",
        uniqueConstraints = @UniqueConstraint(name = "uk_permits_number", columnNames = "permitNumber"),
        indexes = {
                @Index(name = "ix_permits_booking", columnList = "booking_id"),
                @Index(name = "ix_permits_expiry", columnList = "expiryDate")
        })
@Getter
@Setter
@NoArgsConstructor
public class Permit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String permitNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "park_id", nullable = false)
    private Park park;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermitStatus status = PermitStatus.PENDING;

    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer coveredParticipants = 1;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private User requestedBy;

    private Instant approvedAt;

    @Column(nullable = false)
    private Integer renewalCount = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
