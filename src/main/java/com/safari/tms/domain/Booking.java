package com.safari.tms.domain;

import com.safari.tms.domain.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uk_bookings_ref", columnNames = "bookingReference"),
        indexes = {
                @Index(name = "ix_bookings_pkg_date", columnList = "package_id,tripDate"),
                @Index(name = "ix_bookings_customer", columnList = "customer_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private SafariPackage safariPackage;

    @Column(nullable = false)
    private LocalDate tripDate;

    @Column(nullable = false)
    private Integer participants;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(length = 1000)
    private String specialRequests;

    private LocalDate paymentDueDate;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant cancelledAt;

    @Column(length = 500)
    private String cancellationReason;

    @Version
    private Long version;

    @Transient
    public BigDecimal getBalanceDue() {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        return totalPrice.subtract(paid);
    }

    @Transient
    public boolean isFullyPaid() {
        return getBalanceDue().compareTo(BigDecimal.ZERO) <= 0;
    }
}
