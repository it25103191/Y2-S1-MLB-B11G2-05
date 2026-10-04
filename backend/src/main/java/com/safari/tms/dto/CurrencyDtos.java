package com.safari.tms.dto;

import com.safari.tms.domain.ExchangeRate;
import com.safari.tms.domain.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CurrencyDtos {

    public record RateView(
            Currency currency,
            BigDecimal unitsPerUsd,
            Instant updatedAt,
            String updatedByName) {

        public static RateView base() {
            return new RateView(Currency.USD, BigDecimal.ONE, null, null);
        }

        public static RateView of(ExchangeRate r) {
            return new RateView(r.getCurrency(), r.getUnitsPerUsd(), r.getUpdatedAt(),
                    r.getUpdatedBy() == null ? null : r.getUpdatedBy().getFullName());
        }
    }


    public record RatesView(Currency base, List<RateView> rates) {
    }

    public record RateUpdateRequest(
            @NotNull(message = "Enter the exchange rate")
            @DecimalMin(value = "0.0001", message = "The rate must be greater than zero")
            BigDecimal unitsPerUsd) {
    }


    public record Charge(Currency currency, BigDecimal chargedAmount, BigDecimal fxRate) {
    }

    private CurrencyDtos() {
    }
}
