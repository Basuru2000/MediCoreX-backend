package com.medicorex.controller;

import com.medicorex.dto.ExpiryAlertDTO;
import com.medicorex.service.expiry.ExpiryAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/expiry/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExpiryAlertController {

    private final ExpiryAlertService alertService;

    /**
     * Get expiry alerts with pagination and filtering
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Page<ExpiryAlertDTO>> getExpiryAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "alertDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.debug("Fetching expiry alerts - status: {}, page: {}, size: {}", status, page, size);

        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
            
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ExpiryAlertDTO> alerts = alertService.getAlerts(status, pageable);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alert by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryAlertDTO> getAlertById(@PathVariable Long id) {
        ExpiryAlertDTO alert = alertService.getAlertById(id);
        return ResponseEntity.ok(alert);
    }

    /**
     * Acknowledge alert
     */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryAlertDTO> acknowledgeAlert(
            @PathVariable Long id,
            @RequestBody(required = false) String notes) {
        ExpiryAlertDTO alert = alertService.acknowledgeAlert(id, notes);
        return ResponseEntity.ok(alert);
    }

    /**
     * Resolve alert
     */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryAlertDTO> resolveAlert(
            @PathVariable Long id,
            @RequestBody(required = false) String notes) {
        ExpiryAlertDTO alert = alertService.resolveAlert(id, notes);
        return ResponseEntity.ok(alert);
    }

    /**
     * Get alerts count by status
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Long> getAlertsCount(@RequestParam(required = false) String status) {
        Long count = alertService.getAlertsCount(status);
        return ResponseEntity.ok(count);
    }

    /**
     * Get critical alerts (for dashboard)
     */
    @GetMapping("/critical")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ExpiryAlertDTO>> getCriticalAlerts(
            @RequestParam(defaultValue = "5") int limit) {
        List<ExpiryAlertDTO> alerts = alertService.getCriticalAlerts(limit);
        return ResponseEntity.ok(alerts);
    }
}