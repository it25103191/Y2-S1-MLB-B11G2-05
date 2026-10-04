package com.safari.tms.controller;

import com.safari.tms.dto.PermitDtos.*;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.PermitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permits")
@PreAuthorize(Roles.ANY_STAFF)
public class PermitController {

    /** Roles allowed to raise or decide permits. */
    private static final String PERMIT_MANAGERS =
            "hasAnyRole('OPERATIONS_MANAGER','SAFARI_VEHICLE_COORDINATOR','FINANCE_RESERVATIONS_EXECUTIVE')";

    private final PermitService permitService;
    private final AuthService authService;

    public PermitController(PermitService permitService, AuthService authService) {
        this.permitService = permitService;
        this.authService = authService;
    }

    @GetMapping
    public List<PermitView> list() {
        return permitService.findAll();
    }

    @GetMapping("/dashboard")
    public PermitDashboardView dashboard() {
        return permitService.dashboard();
    }

    /** Permits lapsing inside the configured window (override with ?days=). */
    @GetMapping("/expiring")
    public List<PermitView> expiring(@RequestParam(required = false) Integer days) {
        return permitService.findExpiring(days);
    }

    @GetMapping("/awaiting-request")
    public List<UnpermittedBookingView> awaitingRequest() {
        return permitService.awaitingRequest();
    }

    @GetMapping("/booking/{bookingId}")
    public List<PermitView> forBooking(@PathVariable Long bookingId) {
        return permitService.findForBooking(bookingId);
    }

    @GetMapping("/{id}")
    public PermitView detail(@PathVariable Long id) {
        return permitService.findOne(id);
    }

    @PostMapping
    @PreAuthorize(PERMIT_MANAGERS)
    public ResponseEntity<PermitView> request(@Valid @RequestBody PermitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(permitService.request(request, authService.requireCurrentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PERMIT_MANAGERS)
    public PermitView update(@PathVariable Long id, @Valid @RequestBody PermitUpdateRequest request) {
        return permitService.update(id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(PERMIT_MANAGERS)
    public PermitView approve(@PathVariable Long id,
                              @RequestBody(required = false) PermitDecisionRequest request) {
        return permitService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(PERMIT_MANAGERS)
    public PermitView reject(@PathVariable Long id,
                             @RequestBody(required = false) PermitDecisionRequest request) {
        return permitService.reject(id, request);
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize(PERMIT_MANAGERS)
    public PermitView renew(@PathVariable Long id, @Valid @RequestBody PermitRenewRequest request) {
        return permitService.renew(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PERMIT_MANAGERS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        permitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
