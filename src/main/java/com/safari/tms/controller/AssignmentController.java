package com.safari.tms.web;

import com.safari.tms.dto.ResourceDtos.AssignmentRequest;
import com.safari.tms.dto.ResourceDtos.AssignmentStatusRequest;
import com.safari.tms.dto.ResourceDtos.AssignmentView;
import com.safari.tms.dto.ResourceDtos.SuggestionView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AssignmentService;
import com.safari.tms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@PreAuthorize(Roles.ANY_STAFF)
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AuthService authService;

    public AssignmentController(AssignmentService assignmentService, AuthService authService) {
        this.assignmentService = assignmentService;
        this.authService = authService;
    }

    @GetMapping
    public List<AssignmentView> list() {
        return assignmentService.findAll();
    }

    @GetMapping("/{id}")
    public AssignmentView detail(@PathVariable Long id) {
        return assignmentService.findOne(id);
    }

    /** Powers the schedule / calendar view. */
    @GetMapping("/schedule")
    public List<AssignmentView> schedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return assignmentService.findInRange(from, to);
    }

    /** Returns every guide/vehicle with availability for this booking, plus a recommendation. */
    @GetMapping("/suggest/{bookingId}")
    @PreAuthorize(Roles.OPERATIONS)
    public SuggestionView suggest(@PathVariable Long bookingId) {
        return assignmentService.suggest(bookingId);
    }

    @PostMapping
    @PreAuthorize(Roles.OPERATIONS)
    public ResponseEntity<AssignmentView> create(@Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.create(request, authService.requireCurrentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.OPERATIONS)
    public AssignmentView update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.update(id, request, authService.requireCurrentUser());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.OPERATIONS)
    public AssignmentView changeStatus(@PathVariable Long id,
                                       @Valid @RequestBody AssignmentStatusRequest request) {
        return assignmentService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.OPERATIONS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assignmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
