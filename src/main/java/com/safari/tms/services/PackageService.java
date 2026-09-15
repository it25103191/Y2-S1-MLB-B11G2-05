package com.safari.tms.service;

import com.safari.tms.domain.Park;
import com.safari.tms.domain.SafariPackage;
import com.safari.tms.dto.CatalogueDtos.AvailabilityView;
import com.safari.tms.dto.CatalogueDtos.PackageRequest;
import com.safari.tms.dto.CatalogueDtos.PackageView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.BookingRepository;
import com.safari.tms.repo.SafariPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PackageService {

    private final SafariPackageRepository packages;
    private final BookingRepository bookings;
    private final ParkService parkService;

    public PackageService(SafariPackageRepository packages, BookingRepository bookings, ParkService parkService) {
        this.packages = packages;
        this.bookings = bookings;
        this.parkService = parkService;
    }

    @Transactional(readOnly = true)
    public List<PackageView> search(boolean activeOnly, Long parkId, BigDecimal minPrice, BigDecimal maxPrice,
                                    Integer minDays, Integer maxDays, String search) {
        String term = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase() + "%";
        return packages.search(activeOnly, parkId, minPrice, maxPrice, minDays, maxDays, term)
                .stream().map(PackageView::of).toList();
    }

    @Transactional(readOnly = true)
    public PackageView findOne(Long id) {
        SafariPackage found = packages.findDetailById(id);
        if (found == null) {
            throw ApiException.notFound("Safari package", id);
        }
        return PackageView.of(found);
    }

    @Transactional
    public PackageView create(PackageRequest request) {
        Park park = parkService.require(request.parkId());
        SafariPackage pkg = new SafariPackage();
        pkg.setPark(park);
        apply(pkg, request);
        return PackageView.of(packages.save(pkg));
    }

    @Transactional
    public PackageView update(Long id, PackageRequest request) {
        SafariPackage pkg = require(id);
        pkg.setPark(parkService.require(request.parkId()));
        apply(pkg, request);
        return PackageView.of(packages.save(pkg));
    }

    /** Soft delete - packages are never hard-deleted because bookings reference them. */
    @Transactional
    public PackageView setActive(Long id, boolean active) {
        SafariPackage pkg = require(id);
        pkg.setActive(active);
        return PackageView.of(packages.save(pkg));
    }

    @Transactional(readOnly = true)
    public AvailabilityView availability(Long packageId, LocalDate tripDate) {
        SafariPackage pkg = require(packageId);
        int taken = bookings.sumCommittedSeats(packageId, tripDate, null);
        int remaining = Math.max(0, pkg.getMaxGroupSize() - taken);
        return new AvailabilityView(packageId, tripDate, pkg.getMaxGroupSize(), taken, remaining, remaining == 0);
    }

    private void apply(SafariPackage pkg, PackageRequest request) {
        pkg.setName(request.name().trim());
        pkg.setDescription(request.description());
        pkg.setDurationDays(request.durationDays());
        pkg.setPricePerPerson(request.pricePerPerson());
        pkg.setMaxGroupSize(request.maxGroupSize());
        pkg.setImageUrl(request.imageUrl());
        pkg.setHighlights(request.highlights());
        if (request.active() != null) {
            pkg.setActive(request.active());
        }
    }

    @Transactional(readOnly = true)
    public SafariPackage require(Long id) {
        return packages.findById(id).orElseThrow(() -> ApiException.notFound("Safari package", id));
    }
}
