package com.safari.tms.web;

import com.safari.tms.domain.enums.VehicleStatus;
import com.safari.tms.dto.ResourceDtos.VehicleRequest;
import com.safari.tms.dto.ResourceDtos.VehicleView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@PreAuthorize(Roles.ANY_STAFF)
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleView> list() {
        return vehicleService.findAll();
    }

    @GetMapping("/{id}")
    public VehicleView detail(@PathVariable Long id) {
        return vehicleService.findOne(id);
    }

    @PostMapping
    @PreAuthorize(Roles.OPERATIONS)
    public ResponseEntity<VehicleView> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.OPERATIONS)
    public VehicleView update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.OPERATIONS)
    public VehicleView setStatus(@PathVariable Long id, @RequestParam VehicleStatus value) {
        return vehicleService.setStatus(id, value);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.OPERATIONS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
