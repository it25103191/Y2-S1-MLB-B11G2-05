package com.safari.tms.service;

import com.safari.tms.domain.Park;
import com.safari.tms.dto.CatalogueDtos.ParkRequest;
import com.safari.tms.dto.CatalogueDtos.ParkView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.ParkRepository;
import com.safari.tms.repo.SafariPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ParkService {

    private final ParkRepository parks;
    private final SafariPackageRepository packages;

    public ParkService(ParkRepository parks, SafariPackageRepository packages) {
        this.parks = parks;
        this.packages = packages;
    }

    @Transactional(readOnly = true)
    public List<ParkView> findAll(boolean activeOnly) {
        List<Park> found = activeOnly ? parks.findByActiveTrueOrderByNameAsc() : parks.findAllByOrderByNameAsc();
        return found.stream()
                .map(p -> ParkView.of(p, packages.countByParkId(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ParkView findOne(Long id) {
        Park park = require(id);
        return ParkView.of(park, packages.countByParkId(park.getId()));
    }

    @Transactional
    public ParkView create(ParkRequest request) {
        if (parks.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("A park named '" + request.name().trim() + "' already exists");
        }
        Park park = new Park();
        apply(park, request);
        Park saved = parks.save(park);
        return ParkView.of(saved, 0L);
    }

    @Transactional
    public ParkView update(Long id, ParkRequest request) {
        Park park = require(id);
        if (!park.getName().equalsIgnoreCase(request.name().trim())
                && parks.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("A park named '" + request.name().trim() + "' already exists");
        }
        apply(park, request);
        return ParkView.of(parks.save(park), packages.countByParkId(park.getId()));
    }

    @Transactional
    public void delete(Long id) {
        Park park = require(id);
        long linked = packages.countByParkId(id);
        if (linked > 0) {
            throw ApiException.conflict("Cannot delete " + park.getName() + " because " + linked
                    + " safari package(s) still reference it. Deactivate it instead.");
        }
        parks.delete(park);
    }

    private void apply(Park park, ParkRequest request) {
        park.setName(request.name().trim());
        park.setLocation(request.location().trim());
        park.setDescription(request.description());
        park.setEntryFeePerPerson(request.entryFeePerPerson() == null
                ? BigDecimal.ZERO : request.entryFeePerPerson());
        park.setPermitAuthority(request.permitAuthority());
        if (request.active() != null) {
            park.setActive(request.active());
        }
    }

    @Transactional(readOnly = true)
    public Park require(Long id) {
        return parks.findById(id).orElseThrow(() -> ApiException.notFound("Park", id));
    }
}
