package com.safari.tms.controller;

import com.safari.tms.dto.ReportDtos.DashboardView;
import com.safari.tms.security.Roles;
import com.safari.tms.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Read-only analytics. Every response is already shaped for charting. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize(Roles.ANY_STAFF)
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.dashboard(from, to);
    }
}
