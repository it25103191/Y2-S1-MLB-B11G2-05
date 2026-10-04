package com.safari.tms.controller;

import com.safari.tms.dto.AccountDtos.StaffAccountView;
import com.safari.tms.dto.AccountDtos.StaffCreateRequest;
import com.safari.tms.dto.AccountDtos.StaffStatusRequest;
import com.safari.tms.dto.AccountDtos.StaffUpdateRequest;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff-accounts")
@PreAuthorize(Roles.MANAGEMENT)
public class StaffAccountController {

    private final StaffAccountService staffAccounts;
    private final AuthService authService;

    public StaffAccountController(StaffAccountService staffAccounts, AuthService authService) {
        this.staffAccounts = staffAccounts;
        this.authService = authService;
    }

    @GetMapping
    public List<StaffAccountView> list() {
        return staffAccounts.list(authService.requireCurrentUser());
    }

    @PostMapping
    public ResponseEntity<StaffAccountView> create(@Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffAccounts.create(request, authService.requireCurrentUser()));
    }

    @PutMapping("/{id}")
    public StaffAccountView update(@PathVariable Long id, @Valid @RequestBody StaffUpdateRequest request) {
        return staffAccounts.update(id, request, authService.requireCurrentUser());
    }

    @PatchMapping("/{id}/status")
    public StaffAccountView setStatus(@PathVariable Long id, @Valid @RequestBody StaffStatusRequest request) {
        return staffAccounts.setActive(id, request.active(), authService.requireCurrentUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        staffAccounts.remove(id, authService.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
