package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.quarantine.QuarantineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quarantine")
@RequiredArgsConstructor
public class QuarantineController {

    private final QuarantineService quarantineService;

    /**
     * Quarantine a batch
     */
    @PostMapping("/quarantine-batch")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<QuarantineItemDTO> quarantineBatch(
            @RequestParam Long batchId,
            @RequestParam String reason,
            Principal principal) {
        QuarantineItemDTO result = quarantineService.quarantineBatch(
                batchId, reason, principal.getName());
        return ResponseEntity.ok(result);
    }

    /**
     * Get quarantine records with pagination
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Page<QuarantineItemDTO>> getQuarantineRecords(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<QuarantineItemDTO> records = quarantineService.getQuarantineRecords(
                status, PageRequest.of(page, size));
        return ResponseEntity.ok(records);
    }

    /**
     * Get quarantine record by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<QuarantineItemDTO> getQuarantineRecord(@PathVariable Long id) {
        return ResponseEntity.ok(quarantineService.getQuarantineRecord(id));
    }

    /**
     * Process quarantine action
     */
    @PostMapping("/action")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<QuarantineItemDTO> processAction(
            @Valid @RequestBody QuarantineActionDTO actionDTO,
            Principal principal) {
        actionDTO.setPerformedBy(principal.getName());
        QuarantineItemDTO result = quarantineService.processAction(actionDTO);
        return ResponseEntity.ok(result);
    }

    /**
     * Get pending review items
     */
    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<QuarantineItemDTO>> getPendingReview() {
        return ResponseEntity.ok(quarantineService.getPendingReview());
    }

    /**
     * Get quarantine summary
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<QuarantineSummaryDTO> getQuarantineSummary() {
        return ResponseEntity.ok(quarantineService.getQuarantineSummary());
    }

    /**
     * Trigger auto-quarantine for expired batches
     */
    @PostMapping("/auto-quarantine")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> triggerAutoQuarantine() {
        quarantineService.autoQuarantineExpiredBatches();
        return ResponseEntity.ok(Map.of("message", "Auto-quarantine process completed"));
    }
}