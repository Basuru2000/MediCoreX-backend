package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.procurement.ProcurementAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/procurement-analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ProcurementAnalyticsController {

    private final ProcurementAnalyticsService analyticsService;

    /**
     * Get overall procurement metrics
     * GET /api/procurement-analytics/metrics?startDate=2025-01-01&endDate=2025-12-31
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<ProcurementMetricsDTO> getProcurementMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Default to last 12 months if not specified
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(12);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        return ResponseEntity.ok(analyticsService.getProcurementMetrics(startDate, endDate));
    }

    /**
     * Get PO trends over time
     * GET /api/procurement-analytics/trends?months=12
     */
    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<POTrendDataDTO>> getPOTrends(
            @RequestParam(defaultValue = "12") Integer months) {

        if (months < 1 || months > 24) {
            months = 12; // Default to 12 months
        }

        return ResponseEntity.ok(analyticsService.getPOTrends(months));
    }

    /**
     * Get top suppliers by volume or value
     * GET /api/procurement-analytics/top-suppliers?limit=10&sortBy=value
     */
    @GetMapping("/top-suppliers")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<TopSupplierDTO>> getTopSuppliers(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "value") String sortBy) {

        if (limit < 1 || limit > 50) {
            limit = 10; // Default to top 10
        }

        if (!sortBy.equals("value") && !sortBy.equals("volume")) {
            sortBy = "value"; // Default sort
        }

        return ResponseEntity.ok(analyticsService.getTopSuppliers(limit, sortBy));
    }

    /**
     * Get PO status distribution
     * GET /api/procurement-analytics/status-distribution?startDate=2025-01-01&endDate=2025-12-31
     */
    @GetMapping("/status-distribution")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<POStatusDistributionDTO>> getStatusDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Default to last 12 months if not specified
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(12);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        return ResponseEntity.ok(analyticsService.getStatusDistribution(startDate, endDate));
    }
}