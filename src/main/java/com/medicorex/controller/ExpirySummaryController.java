package com.medicorex.controller;

import com.medicorex.dto.ExpirySummaryDTO;
import com.medicorex.dto.ExpirySummaryDTO.CriticalItemDTO;
import com.medicorex.service.expiry.ExpirySummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/expiry/summary")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExpirySummaryController {

    private final ExpirySummaryService expirySummaryService;

    /**
     * Get comprehensive expiry summary for dashboard
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<ExpirySummaryDTO> getExpirySummary() {
        log.debug("Fetching expiry summary for dashboard");
        ExpirySummaryDTO summary = expirySummaryService.getExpirySummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Get critical alerts only
     */
    @GetMapping("/critical")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<CriticalItemDTO>> getCriticalAlerts(
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("Fetching top {} critical alerts", limit);
        List<CriticalItemDTO> criticalAlerts = expirySummaryService.getCriticalAlerts(limit);
        return ResponseEntity.ok(criticalAlerts);
    }

    /**
     * Get summary counts only (lightweight endpoint)
     */
    @GetMapping("/counts")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Map<String, Long>> getSummaryCounts() {
        log.debug("Fetching summary counts");
        ExpirySummaryDTO summary = expirySummaryService.getExpirySummary();

        Map<String, Long> counts = Map.of(
                "expired", summary.getExpiredCount(),
                "expiringToday", summary.getExpiringTodayCount(),
                "expiringThisWeek", summary.getExpiringThisWeekCount(),
                "expiringThisMonth", summary.getExpiringThisMonthCount(),
                "pendingAlerts", summary.getPendingAlertsCount(),
                "quarantined", summary.getQuarantinedItemsCount()
        );

        return ResponseEntity.ok(counts);
    }

    /**
     * Get financial impact summary
     */
    @GetMapping("/financial-impact")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> getFinancialImpact() {
        log.debug("Fetching financial impact summary");
        ExpirySummaryDTO summary = expirySummaryService.getExpirySummary();

        Map<String, Object> impact = Map.of(
                "totalValueAtRisk", summary.getTotalValueAtRisk(),
                "expiredValue", summary.getExpiredValue(),
                "currency", "USD"
        );

        return ResponseEntity.ok(impact);
    }
}