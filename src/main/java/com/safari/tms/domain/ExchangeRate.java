package com.safari.tms.domain;

import com.safari.tms.domain.enums.Currency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** How many units of a currency one US dollar buys. USD itself is the base and is never stored. */
@Entity
@Table(name = "exchange_rates",
        uniqueConstraints = @UniqueConstraint(name = "uk_exchange_rates_currency", columnNames = "currency"))
@Getter
@Setter
@NoArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal unitsPerUsd;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    public ExchangeRate(Currency currency, BigDecimal unitsPerUsd) {
        this.currency = currency;
        this.unitsPerUsd = unitsPerUsd;
    }
}
