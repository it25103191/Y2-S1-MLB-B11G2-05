package com.safari.tms.service;

import com.safari.tms.domain.Guide;
import com.safari.tms.domain.enums.GuideStatus;
import com.safari.tms.dto.ResourceDtos.GuideRequest;
import com.safari.tms.dto.ResourceDtos.GuideView;
import com.safari.tms.exception.ApiException;
import com.safari.tms.repo.AssignmentRepository;
import com.safari.tms.repo.GuideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class GuideService {

    private final GuideRepository guides;
    private final AssignmentRepository assignments;

    public GuideService(GuideRepository guides, AssignmentRepository assignments) {
        this.guides = guides;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public List<GuideView> findAll() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusYears(2);
        return guides.findAllByOrderByFullNameAsc().stream()
                .map(g -> GuideView.of(g, assignments.countGuideAssignments(g.getId(), today, horizon)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GuideView findOne(Long id) {
        Guide g = require(id);
        LocalDate today = LocalDate.now();
        return GuideView.of(g, assignments.countGuideAssignments(g.getId(), today, today.plusYears(2)));
    }

    @Transactional
    public GuideView create(GuideRequest request) {
        String licence = request.licenseNumber().trim();
        if (guides.existsByLicenseNumberIgnoreCase(licence)) {
            throw ApiException.conflict("Licence number " + licence + " is already on file.");
        }
        Guide g = new Guide();
        g.setLicenseNumber(licence);
        apply(g, request);
        return GuideView.of(guides.save(g), 0L);
    }

    @Transactional
    public GuideView update(Long id, GuideRequest request) {
        Guide g = require(id);
        String licence = request.licenseNumber().trim();
        if (!g.getLicenseNumber().equalsIgnoreCase(licence)
                && guides.existsByLicenseNumberIgnoreCase(licence)) {
            throw ApiException.conflict("Licence number " + licence + " is already on file.");
        }
        g.setLicenseNumber(licence);
        apply(g, request);
        Guide saved = guides.save(g);
        LocalDate today = LocalDate.now();
        return GuideView.of(saved, assignments.countGuideAssignments(saved.getId(), today, today.plusYears(2)));
    }

    @Transactional
    public GuideView setStatus(Long id, GuideStatus status) {
        Guide g = require(id);
        g.setStatus(status);
        Guide saved = guides.save(g);
        LocalDate today = LocalDate.now();
        return GuideView.of(saved, assignments.countGuideAssignments(saved.getId(), today, today.plusYears(2)));
    }

    @Transactional
    public void delete(Long id) {
        Guide g = require(id);
        LocalDate today = LocalDate.now();
        long upcoming = assignments.countGuideAssignments(id, today, today.plusYears(5));
        if (upcoming > 0) {
            throw ApiException.conflict("Cannot delete " + g.getFullName() + " — they still have "
                    + upcoming + " upcoming assignment(s). Set them inactive instead.");
        }
        guides.delete(g);
    }

    private void apply(Guide g, GuideRequest request) {
        g.setFullName(request.fullName().trim());
        g.setEmail(request.email());
        g.setPhone(request.phone());
        g.setLanguages(request.languages());
        g.setSpecialization(request.specialization());
        g.setYearsExperience(request.yearsExperience() == null ? 0 : request.yearsExperience());
        if (request.status() != null) {
            g.setStatus(request.status());
        }
    }

    @Transactional(readOnly = true)
    public Guide require(Long id) {
        return guides.findById(id).orElseThrow(() -> ApiException.notFound("Guide", id));
    }
}
