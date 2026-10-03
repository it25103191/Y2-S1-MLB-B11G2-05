package com.safari.tms.controller;

import com.safari.tms.dto.FinanceDtos.RefundDecisionRequest;
import com.safari.tms.dto.FinanceDtos.RefundQuoteView;
import com.safari.tms.dto.FinanceDtos.RefundRequestBody;
import com.safari.tms.dto.FinanceDtos.RefundUpdateRequest;
import com.safari.tms.dto.FinanceDtos.RefundView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;
    private final AuthService authService;

    public RefundController(RefundService refundService, AuthService authService) {
        this.refundService = refundService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY_STAFF)
    public List<RefundView> list() {
        return refundService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY_STAFF)
    public RefundView detail(@PathVariable Long id) {
        return refundService.findOne(id);
    }

    /** Refunds against one booking. Customers may read their own. */
    @GetMapping("/booking/{bookingId}")
    public List<RefundView> forBooking(@PathVariable Long bookingId) {
        return refundService.findForBooking(bookingId, authService.requireCurrentUser());
    }

    /** What the cancellation-window policy would award for this booking today. */
    @GetMapping("/quote/{bookingId}")
    public RefundQuoteView quote(@PathVariable Long bookingId) {
        return refundService.quote(bookingId, authService.requireCurrentUser());
    }

    @PostMapping
    public ResponseEntity<RefundView> request(@Valid @RequestBody RefundRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundService.request(body, authService.requireCurrentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.FINANCE)
    public RefundView update(@PathVariable Long id, @Valid @RequestBody RefundUpdateRequest request) {
        return refundService.update(id, request);
    }

    /** Withdraws a refund that is still awaiting a decision. */
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.FINANCE)
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        refundService.withdraw(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(Roles.FINANCE)
    public RefundView approve(@PathVariable Long id,
                              @RequestBody(required = false) RefundDecisionRequest request) {
        return refundService.approve(id, request, authService.requireCurrentUser());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(Roles.FINANCE)
    public RefundView reject(@PathVariable Long id,
                             @RequestBody(required = false) RefundDecisionRequest request) {
        return refundService.reject(id, request, authService.requireCurrentUser());
    }

    @PostMapping("/{id}/process")
    @PreAuthorize(Roles.FINANCE)
    public RefundView process(@PathVariable Long id) {
        return refundService.process(id, authService.requireCurrentUser());
    }
}
