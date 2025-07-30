package com.medicorex.controller;

import com.medicorex.dto.ExpiryCheckResultDTO;
import com.medicorex.service.expiry.ExpiryMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/expiry/monitoring")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExpiryMonitoringController {

    private final ExpiryMonitoringService monitoringService;

    /**
     * Manually trigger expiry check
     */
    @PostMapping("/check")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<ExpiryCheckResultDTO> triggerExpiryCheck() {
        ExpiryCheckResultDTO result = monitoringService.performManualExpiryCheck();
        return ResponseEntity.ok(result);
    }

    /**
     * Force expiry check (bypasses restrictions) - Admin only
     */
    @PostMapping("/check/force")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<ExpiryCheckResultDTO> forceExpiryCheck() {
        ExpiryCheckResultDTO result = monitoringService.forceExpiryCheck();
        return ResponseEntity.ok(result);
    }

    /**
     * Get check history
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ExpiryCheckResultDTO>> getCheckHistory() {
        return ResponseEntity.ok(monitoringService.getCheckHistory());
    }

    /**
     * Get check status for specific date
     */
    @GetMapping("/status/{date}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryCheckResultDTO> getCheckStatus(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExpiryCheckResultDTO status = monitoringService.getCheckStatus(date);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Get today's check summary
     */
    @GetMapping("/today-summary")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, Object>> getTodayCheckSummary() {
        return ResponseEntity.ok(monitoringService.getTodayCheckSummary());
    }

    /**
     * Get monitoring dashboard stats
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = Map.of(
                "lastCheckDate", LocalDate.now(),
                "checkHistory", monitoringService.getCheckHistory(),
                "todaySummary", monitoringService.getTodayCheckSummary(),
                "message", "Dashboard stats endpoint"
        );
        return ResponseEntity.ok(stats);
    }
}