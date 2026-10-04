package com.safari.tms.service;

import com.safari.tms.domain.ExchangeRate;
import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Currency;
import com.safari.tms.dto.CurrencyDtos.Charge;
import com.safari.tms.dto.CurrencyDtos.RateView;
import com.safari.tms.dto.CurrencyDtos.RatesView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Service
public class CurrencyService {


    static final BigDecimal LKR_MIN = new BigDecimal("50");
    static final BigDecimal LKR_MAX = new BigDecimal("1000");


    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    private final ExchangeRateRepository rates;

    public CurrencyService(ExchangeRateRepository rates) {
        this.rates = rates;
    }

    @Transactional(readOnly = true)
    public RatesView list() {
        List<RateView> views = new ArrayList<>();
        views.add(RateView.base());
        rates.findAllDetailed().stream()
                .filter(r -> r.getCurrency() != Currency.USD)
                .map(RateView::of)
                .forEach(views::add);
        return new RatesView(Currency.USD, views);
    }

    @Transactional(readOnly = true)
    public BigDecimal unitsPerUsd(Currency currency) {
        if (currency == null || currency == Currency.USD) {
            return BigDecimal.ONE;
        }
        return rates.findByCurrency(currency)
                .map(ExchangeRate::getUnitsPerUsd)
                .orElseThrow(() -> ApiException.conflict("No exchange rate is set for " + currency
                        + " yet. Ask the finance team to add one."));
    }

    @Transactional
    public RateView update(Currency currency, BigDecimal unitsPerUsd, User staff) {
        if (currency == Currency.USD) {
            throw ApiException.badRequest("USD is the base currency, so its rate is always 1.");
        }
        if (currency == Currency.LKR
                && (unitsPerUsd.compareTo(LKR_MIN) < 0 || unitsPerUsd.compareTo(LKR_MAX) > 0)) {
            throw ApiException.badRequest("That looks wrong for LKR: enter how many rupees one US dollar buys, "
                    + "between " + LKR_MIN + " and " + LKR_MAX + ".");
        }
        ExchangeRate rate = rates.findByCurrency(currency).orElseGet(() -> new ExchangeRate(currency, unitsPerUsd));
        rate.setUnitsPerUsd(unitsPerUsd.setScale(4, RoundingMode.HALF_UP));
        rate.setUpdatedAt(Instant.now());
        rate.setUpdatedBy(staff);
        return RateView.of(rates.save(rate));
    }


    @Transactional(readOnly = true)
    public Charge charge(BigDecimal usdAmount, Currency currency, BigDecimal clientCharged) {
        Currency c = currency == null ? Currency.USD : currency;
        BigDecimal rate = unitsPerUsd(c);
        BigDecimal expected = usdAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        if (clientCharged == null) {
            return new Charge(c, expected, rate);
        }
        BigDecimal tolerance = rate.multiply(ONE_CENT).max(ONE_CENT);
        if (clientCharged.subtract(expected).abs().compareTo(tolerance) > 0) {
            throw ApiException.badRequest("The " + c + " amount no longer matches today's exchange rate "
                    + "(1 USD = " + rate.stripTrailingZeros().toPlainString() + " " + c
                    + "). Refresh the page and try again.");
        }
        return new Charge(c, clientCharged.setScale(2, RoundingMode.HALF_UP), rate);
    }
}
