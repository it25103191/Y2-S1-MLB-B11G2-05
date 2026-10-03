package com.safari.tms.controller;

import com.safari.tms.domain.enums.Currency;
import com.safari.tms.dto.CurrencyDtos.RateUpdateRequest;
import com.safari.tms.dto.CurrencyDtos.RateView;
import com.safari.tms.dto.CurrencyDtos.RatesView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.CurrencyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/currency")
public class CurrencyController {

    private final CurrencyService currencyService;
    private final AuthService authService;

    public CurrencyController(CurrencyService currencyService, AuthService authService) {
        this.currencyService = currencyService;
        this.authService = authService;
    }

    /** Public, so visitors see prices in their own currency before they sign in. */
    @GetMapping
    public RatesView rates() {
        return currencyService.list();
    }

    @PutMapping("/{currency}")
    @PreAuthorize(Roles.FINANCE)
    public RateView update(@PathVariable Currency currency, @Valid @RequestBody RateUpdateRequest body) {
        return currencyService.update(currency, body.unitsPerUsd(), authService.requireCurrentUser());
    }
}
