package com.safari.tms.web;

import com.safari.tms.dto.BookingDtos.QuoteView;
import com.safari.tms.dto.CatalogueDtos.AvailabilityView;
import com.safari.tms.dto.CatalogueDtos.PackageRequest;
import com.safari.tms.dto.CatalogueDtos.PackageView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.BookingService;
import com.safari.tms.service.PackageService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private final PackageService packageService;
    private final BookingService bookingService;

    public PackageController(PackageService packageService, BookingService bookingService) {
        this.packageService = packageService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<PackageView> list(@RequestParam(defaultValue = "true") boolean activeOnly,
                                  @RequestParam(required = false) Long parkId,
                                  @RequestParam(required = false) BigDecimal minPrice,
                                  @RequestParam(required = false) BigDecimal maxPrice,
                                  @RequestParam(required = false) Integer minDays,
                                  @RequestParam(required = false) Integer maxDays,
                                  @RequestParam(required = false) String search) {
        return packageService.search(activeOnly, parkId, minPrice, maxPrice, minDays, maxDays, search);
    }

    @GetMapping("/{id}")
    public PackageView detail(@PathVariable Long id) {
        return packageService.findOne(id);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityView availability(@PathVariable Long id,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return packageService.availability(id, date);
    }

    @GetMapping("/{id}/quote")
    public QuoteView quote(@PathVariable Long id,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(defaultValue = "1") int participants,
                           @RequestParam(required = false) Long excludeBookingId) {
        return bookingService.quote(id, date, participants, excludeBookingId);
    }

    @PostMapping
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public ResponseEntity<PackageView> create(@Valid @RequestBody PackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public PackageView update(@PathVariable Long id, @Valid @RequestBody PackageRequest request) {
        return packageService.update(id, request);
    }

    /** Only succeeds for a package that has never been booked; otherwise deactivate it. */
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        packageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize(Roles.CATALOGUE_MANAGERS)
    public PackageView setActive(@PathVariable Long id, @RequestParam boolean value) {
        return packageService.setActive(id, value);
    }
}
