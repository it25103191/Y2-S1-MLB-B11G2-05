package com.safari.tms.web;

import com.safari.tms.domain.enums.GuideStatus;
import com.safari.tms.dto.ResourceDtos.GuideRequest;
import com.safari.tms.dto.ResourceDtos.GuideView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.GuideService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guides")
@PreAuthorize(Roles.ANY_STAFF)
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping
    public List<GuideView> list() {
        return guideService.findAll();
    }

    @GetMapping("/{id}")
    public GuideView detail(@PathVariable Long id) {
        return guideService.findOne(id);
    }

    @PostMapping
    @PreAuthorize(Roles.OPERATIONS)
    public ResponseEntity<GuideView> create(@Valid @RequestBody GuideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guideService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.OPERATIONS)
    public GuideView update(@PathVariable Long id, @Valid @RequestBody GuideRequest request) {
        return guideService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.OPERATIONS)
    public GuideView setStatus(@PathVariable Long id, @RequestParam GuideStatus value) {
        return guideService.setStatus(id, value);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.OPERATIONS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        guideService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
