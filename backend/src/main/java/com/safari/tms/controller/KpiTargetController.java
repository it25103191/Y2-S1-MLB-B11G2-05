package com.safari.tms.controller;

import com.safari.tms.dto.ReportDtos.KpiTargetRequest;
import com.safari.tms.dto.ReportDtos.KpiTargetView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.KpiTargetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Every staff member can see targets; only management and finance can set them. */
@RestController
@RequestMapping("/api/kpi-targets")
@PreAuthorize(Roles.ANY_STAFF)
public class KpiTargetController {

    private final KpiTargetService targetService;
    private final AuthService authService;

    public KpiTargetController(KpiTargetService targetService, AuthService authService) {
        this.targetService = targetService;
        this.authService = authService;
    }

    @GetMapping
    public List<KpiTargetView> list() {
        return targetService.findAll();
    }

    @GetMapping("/{id}")
    public KpiTargetView detail(@PathVariable Long id) {
        return targetService.findOne(id);
    }

    @PostMapping
    @PreAuthorize(Roles.TARGET_SETTERS)
    public ResponseEntity<KpiTargetView> create(@Valid @RequestBody KpiTargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(targetService.create(request, authService.requireCurrentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.TARGET_SETTERS)
    public KpiTargetView update(@PathVariable Long id, @Valid @RequestBody KpiTargetRequest request) {
        return targetService.update(id, request, authService.requireCurrentUser());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.TARGET_SETTERS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        targetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
