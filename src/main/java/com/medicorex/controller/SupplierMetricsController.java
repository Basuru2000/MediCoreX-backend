package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.supplier.SupplierMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier-metrics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SupplierMetricsController {

    private final SupplierMetricsService metricsService;

    /**
     * Get current month metrics for a supplier
     */
    @GetMapping("/supplier/{supplierId}/current")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierMetricsDTO> getCurrentMetrics(@PathVariable Long supplierId) {
        return ResponseEntity.ok(metricsService.getCurrentMetrics(supplierId));
    }

    /**
     * Get metrics for a specific month
     */
    @GetMapping("/supplier/{supplierId}/month")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierMetricsDTO> getMetricsForMonth(
            @PathVariable Long supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return ResponseEntity.ok(metricsService.getMetricsForMonth(supplierId, month));
    }

    /**
     * Get historical metrics for a supplier
     */
    @GetMapping("/supplier/{supplierId}/history")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<SupplierMetricsDTO>> getHistoricalMetrics(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(metricsService.getHistoricalMetrics(supplierId, months));
    }

    /**
     * Get supplier performance comparison
     */
    @GetMapping("/comparison")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<SupplierPerformanceDTO>> getSupplierComparison(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {

        List<SupplierPerformanceDTO> performances = metricsService.getSupplierComparison(month);

        // Add ranking
        for (int i = 0; i < performances.size(); i++) {
            performances.get(i).setRank(i + 1);
        }

        return ResponseEntity.ok(performances);
    }

    /**
     * Get metrics summary for dashboard
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<MetricsCalculationDTO> getMetricsSummary() {
        return ResponseEntity.ok(metricsService.getMetricsSummary());
    }

    /**
     * Update delivery metrics (called from order/delivery processing)
     */
    @PostMapping("/supplier/{supplierId}/delivery")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, String>> updateDeliveryMetrics(
            @PathVariable Long supplierId,
            @RequestParam boolean onTime) {

        metricsService.updateDeliveryMetrics(supplierId, onTime);
        return ResponseEntity.ok(Map.of("status", "Delivery metrics updated"));
    }

    /**
     * Update quality metrics (called from goods receipt/inspection)
     */
    @PostMapping("/supplier/{supplierId}/quality")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, String>> updateQualityMetrics(
            @PathVariable Long supplierId,
            @RequestParam int itemsReceived,
            @RequestParam int itemsAccepted) {

        metricsService.updateQualityMetrics(supplierId, itemsReceived, itemsAccepted);
        return ResponseEntity.ok(Map.of("status", "Quality metrics updated"));
    }

    /**
     * Update pricing metrics (called from purchase order processing)
     */
    @PostMapping("/supplier/{supplierId}/pricing")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Map<String, String>> updatePricingMetrics(
            @PathVariable Long supplierId,
            @RequestParam BigDecimal orderAmount,
            @RequestParam(required = false) BigDecimal marketPrice) {

        metricsService.updatePricingMetrics(supplierId, orderAmount, marketPrice);
        return ResponseEntity.ok(Map.of("status", "Pricing metrics updated"));
    }

    /**
     * Manually trigger metrics calculation for a supplier
     */
    @PostMapping("/supplier/{supplierId}/calculate")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<SupplierMetricsDTO> calculateMetrics(
            @PathVariable Long supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(metricsService.calculateSupplierMetrics(supplierId, month));
    }
}