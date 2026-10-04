package com.safari.tms.controller;

import com.safari.tms.domain.User;
import com.safari.tms.domain.enums.Role;
import com.safari.tms.dto.RelationsDtos.*;
import com.safari.tms.security.Roles;
import com.safari.tms.service.AuthService;
import com.safari.tms.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final AuthService authService;

    public ComplaintController(ComplaintService complaintService, AuthService authService) {
        this.complaintService = complaintService;
        this.authService = authService;
    }

    /** Staff see every case; customers see only their own. */
    @GetMapping
    public List<ComplaintView> list() {
        User caller = authService.requireCurrentUser();
        return caller.getRole() == Role.CUSTOMER
                ? complaintService.findForCustomer(caller.getId())
                : complaintService.findAll();
    }

    @GetMapping("/mine")
    public List<ComplaintView> mine() {
        return complaintService.findForCustomer(authService.requireCurrentUser().getId());
    }

    @GetMapping("/{id}")
    public ComplaintView detail(@PathVariable Long id) {
        return complaintService.findOne(id, authService.requireCurrentUser());
    }

    @GetMapping("/{id}/timeline")
    public List<CommunicationView> timeline(@PathVariable Long id) {
        return complaintService.timeline(id, authService.requireCurrentUser());
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Roles.CUSTOMER + "')")
    public ResponseEntity<ComplaintView> create(@Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.create(authService.requireCurrentUser(), request));
    }

    /** Both sides can post to the timeline; customers may only post to their own case. */
    @PostMapping("/{id}/notes")
    public ResponseEntity<CommunicationView> addNote(@PathVariable Long id,
                                                     @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.addNote(id, request, authService.requireCurrentUser()));
    }

    /** Customers withdraw their own open case; relations staff can delete any case. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','CUSTOMER_RELATIONS_OFFICER','OPERATIONS_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        complaintService.delete(id, authService.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }

    /** Authors can edit their own hand-written notes. */
    @PutMapping("/{id}/notes/{noteId}")
    public CommunicationView updateNote(@PathVariable Long id, @PathVariable Long noteId,
                                        @Valid @RequestBody NoteUpdateRequest request) {
        return complaintService.updateNote(id, noteId, request, authService.requireCurrentUser());
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId) {
        complaintService.deleteNote(id, noteId, authService.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.RELATIONS)
    public ComplaintView update(@PathVariable Long id, @Valid @RequestBody ComplaintUpdateRequest request) {
        return complaintService.update(id, request, authService.requireCurrentUser());
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize(Roles.RELATIONS)
    public ComplaintView escalate(@PathVariable Long id, @Valid @RequestBody EscalateRequest request) {
        return complaintService.escalate(id, request, authService.requireCurrentUser());
    }
}
