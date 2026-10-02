package com.safari.tms.service;

import com.safari.tms.domain.Vehicle;
import com.safari.tms.domain.enums.VehicleStatus;
import com.safari.tms.dto.ResourceDtos.VehicleRequest;
import com.safari.tms.dto.ResourceDtos.VehicleView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.AssignmentRepository;
import com.safari.tms.repo.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final AssignmentRepository assignments;

    public VehicleService(VehicleRepository vehicles, AssignmentRepository assignments) {
        this.vehicles = vehicles;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public List<VehicleView> findAll() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusYears(2);
        return vehicles.findAllByOrderByRegistrationNumberAsc().stream()
                .map(v -> VehicleView.of(v, assignments.countVehicleAssignments(v.getId(), today, horizon)))
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleView findOne(Long id) {
        Vehicle v = require(id);
        LocalDate today = LocalDate.now();
        return VehicleView.of(v, assignments.countVehicleAssignments(v.getId(), today, today.plusYears(2)));
    }

    @Transactional
    public VehicleView create(VehicleRequest request) {
        String reg = request.registrationNumber().trim();
        if (vehicles.existsByRegistrationNumberIgnoreCase(reg)) {
            throw ApiException.conflict("A vehicle with registration " + reg + " is already registered.");
        }
        Vehicle v = new Vehicle();
        v.setRegistrationNumber(reg);
        apply(v, request);
        return VehicleView.of(vehicles.save(v), 0L);
    }

    @Transactional
    public VehicleView update(Long id, VehicleRequest request) {
        Vehicle v = require(id);
        String reg = request.registrationNumber().trim();
        if (!v.getRegistrationNumber().equalsIgnoreCase(reg)
                && vehicles.existsByRegistrationNumberIgnoreCase(reg)) {
            throw ApiException.conflict("A vehicle with registration " + reg + " is already registered.");
        }
        v.setRegistrationNumber(reg);
        apply(v, request);
        Vehicle saved = vehicles.save(v);
        LocalDate today = LocalDate.now();
        return VehicleView.of(saved, assignments.countVehicleAssignments(saved.getId(), today, today.plusYears(2)));
    }

    @Transactional
    public VehicleView setStatus(Long id, VehicleStatus status) {
        Vehicle v = require(id);
        v.setStatus(status);
        Vehicle saved = vehicles.save(v);
        LocalDate today = LocalDate.now();
        return VehicleView.of(saved, assignments.countVehicleAssignments(saved.getId(), today, today.plusYears(2)));
    }

    @Transactional
    public void delete(Long id) {
        Vehicle v = require(id);
        LocalDate today = LocalDate.now();
        long upcoming = assignments.countVehicleAssignments(id, today, today.plusYears(5));
        if (upcoming > 0) {
            throw ApiException.conflict("Cannot delete " + v.getRegistrationNumber() + " — it still has "
                    + upcoming + " upcoming assignment(s). Mark it retired instead.");
        }
        vehicles.delete(v);
    }

    private void apply(Vehicle v, VehicleRequest request) {
        v.setModel(request.model().trim());
        v.setType(request.type().trim());
        v.setCapacity(request.capacity());
        if (request.status() != null) {
            v.setStatus(request.status());
        }
        v.setLastServiceDate(request.lastServiceDate());
        v.setNotes(request.notes());
    }

    @Transactional(readOnly = true)
    public Vehicle require(Long id) {
        return vehicles.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle", id));
    }
}
