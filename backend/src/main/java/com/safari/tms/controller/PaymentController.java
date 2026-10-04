package com.safari.tms.controller;

import com.safari.tms.dto.FinanceDtos.*;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthService authService;

    public PaymentController(PaymentService paymentService, AuthService authService) {
        this.paymentService = paymentService;
        this.authService = authService;
    }


    @GetMapping
    @PreAuthorize(Roles.ANY_STAFF)
    public List<PaymentView> list() {
        return paymentService.findAll();
    }

    @GetMapping("/summary")
    @PreAuthorize(Roles.ANY_STAFF)
    public FinanceSummaryView summary() {
        return paymentService.summary();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY_STAFF)
    public PaymentView detail(@PathVariable Long id) {
        return paymentService.findOne(id);
    }

    // Payments for one booking. Customers may read their own//
    @GetMapping("/booking/{bookingId}")
    public List<PaymentView> forBooking(@PathVariable Long bookingId) {
        return paymentService.findForBooking(bookingId, authService.requireCurrentUser());
    }

    // On-screen invoice , Customers can  read their own. //
    @GetMapping("/invoice/{bookingId}")
    public InvoiceView invoice(@PathVariable Long bookingId) {
        return paymentService.invoice(bookingId, authService.requireCurrentUser());
    }

    //Records offline payments (cash, bank transfer, mobile money). Skips the card gateway//
    @PostMapping("/offline")
    @PreAuthorize(Roles.FINANCE)
    public ResponseEntity<PaymentResultView> recordOffline(@Valid @RequestBody OfflinePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.recordOffline(request, authService.requireCurrentUser()));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(Roles.FINANCE)
    public PaymentView voidPayment(@PathVariable Long id, @Valid @RequestBody VoidPaymentRequest request) {
        return paymentService.voidPayment(id, request.reason(), authService.requireCurrentUser());
    }


    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.FINANCE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.deleteFailed(id);
        return ResponseEntity.noContent().build();
    }


    @PostMapping
    public ResponseEntity<PaymentResultView> pay(@Valid @RequestBody PaymentRequest request) {
        PaymentResultView result = paymentService.process(request, authService.requireCurrentUser());
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
