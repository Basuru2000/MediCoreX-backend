package com.medicorex.controller;

import com.medicorex.dto.AutoPOConfigDTO;
import com.medicorex.dto.AutoPOGenerationResultDTO;
import com.medicorex.service.procurement.AutoPOGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auto-po")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AutoPOConfigurationController {

    private final AutoPOGenerationService autoPOService;

    /**
     * Get current auto PO configuration
     */
    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<AutoPOConfigDTO> getConfiguration() {
        return ResponseEntity.ok(autoPOService.getConfiguration());
    }

    /**
     * Update auto PO configuration
     */
    @PutMapping("/config")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<AutoPOConfigDTO> updateConfiguration(
            @Valid @RequestBody AutoPOConfigDTO configDTO) {
        return ResponseEntity.ok(autoPOService.updateConfiguration(configDTO));
    }

    /**
     * Manually trigger auto PO generation
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<AutoPOGenerationResultDTO> generatePurchaseOrders() {
        return ResponseEntity.ok(autoPOService.generatePurchaseOrdersManually());
    }
}