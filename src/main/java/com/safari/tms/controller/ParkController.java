package com.safari.tms.web;

import com.safari.tms.dto.CatalogueDtos.ParkRequest;
import com.safari.tms.dto.CatalogueDtos.ParkView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.ParkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parks")
public class ParkController {

    private final ParkService parkService;

    public ParkController(ParkService parkService) {
        this.parkService = parkService;
    }

    @GetMapping
    public List<ParkView> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return parkService.findAll(activeOnly);
    }

    @GetMapping("/{id}")
    public ParkView detail(@PathVariable Long id) {
        return parkService.findOne(id);
    }

    @PostMapping
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public ResponseEntity<ParkView> create(@Valid @RequestBody ParkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public ParkView update(@PathVariable Long id, @Valid @RequestBody ParkRequest request) {
        return parkService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parkService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
