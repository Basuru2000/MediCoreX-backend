package com.medicorex.controller;

import com.medicorex.dto.ExpiryAlertConfigCreateDTO;
import com.medicorex.dto.ExpiryAlertConfigDTO;
import com.medicorex.service.expiry.ExpiryAlertConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expiry/configs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExpiryAlertConfigController {

    private final ExpiryAlertConfigService configService;

    /**
     * Get all alert configurations
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ExpiryAlertConfigDTO>> getAllConfigurations() {
        return ResponseEntity.ok(configService.getAllConfigurations());
    }

    /**
     * Get active alert configurations
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ExpiryAlertConfigDTO>> getActiveConfigurations() {
        return ResponseEntity.ok(configService.getActiveConfigurations());
    }

    /**
     * Get configuration by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryAlertConfigDTO> getConfigurationById(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getConfigurationById(id));
    }

    /**
     * Get configurations for specific role
     */
    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ExpiryAlertConfigDTO>> getConfigurationsForRole(
            @PathVariable String role) {
        return ResponseEntity.ok(configService.getConfigurationsForRole(role));
    }

    /**
     * Create new configuration (Hospital Manager only)
     */
    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<ExpiryAlertConfigDTO> createConfiguration(
            @Valid @RequestBody ExpiryAlertConfigCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configService.createConfiguration(dto));
    }

    /**
     * Update configuration (Hospital Manager only)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<ExpiryAlertConfigDTO> updateConfiguration(
            @PathVariable Long id,
            @Valid @RequestBody ExpiryAlertConfigCreateDTO dto) {
        return ResponseEntity.ok(configService.updateConfiguration(id, dto));
    }

    /**
     * Delete configuration (Hospital Manager only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Void> deleteConfiguration(@PathVariable Long id) {
        configService.deleteConfiguration(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle configuration status (Hospital Manager only)
     */
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<ExpiryAlertConfigDTO> toggleConfigurationStatus(
            @PathVariable Long id) {
        return ResponseEntity.ok(configService.toggleConfigurationStatus(id));
    }

    /**
     * Update sort order for multiple configurations
     */
    @PutMapping("/sort-order")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> updateSortOrders(
            @RequestBody List<Long> configIds) {
        configService.updateSortOrders(configIds);
        return ResponseEntity.ok(Map.of("message", "Sort order updated successfully"));
    }

    /**
     * Get affected product count for a configuration
     */
    @GetMapping("/{id}/affected-products")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, Long>> getAffectedProductCount(@PathVariable Long id) {
        Long count = configService.getAffectedProductCount(id);
        return ResponseEntity.ok(Map.of("count", count));
    }
}