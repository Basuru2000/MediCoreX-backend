package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.ProductBatchService;
import com.medicorex.service.ProductBatchService.BatchConsumptionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ProductBatchController {

    private final ProductBatchService batchService;

    /**
     * Create new batch
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductBatchDTO> createBatch(@Valid @RequestBody ProductBatchCreateDTO dto) {
        ProductBatchDTO batch = batchService.createBatch(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(batch);
    }

    /**
     * Get all batches for a product
     */
    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<ProductBatchDTO>> getBatchesByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(batchService.getBatchesByProduct(productId));
    }

    /**
     * Get batch by ID
     */
    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductBatchDTO> getBatchById(@PathVariable Long batchId) {
        return ResponseEntity.ok(batchService.getBatchById(batchId));
    }

    /**
     * Consume stock from batches (FIFO)
     */
    @PostMapping("/consume")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<BatchConsumptionResult>> consumeStock(
            @RequestParam Long productId,
            @RequestParam Integer quantity,
            @RequestParam String reason) {
        List<BatchConsumptionResult> results = batchService.consumeStock(productId, quantity, reason);
        return ResponseEntity.ok(results);
    }

    /**
     * Adjust batch stock
     */
    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductBatchDTO> adjustBatchStock(@Valid @RequestBody BatchStockAdjustmentDTO dto) {
        ProductBatchDTO batch = batchService.adjustBatchStock(dto);
        return ResponseEntity.ok(batch);
    }

    /**
     * Get expiring batches
     */
    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ProductBatchDTO>> getExpiringBatches(
            @RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(batchService.getExpiringBatches(daysAhead));
    }

    /**
     * Get batch expiry report
     */
    @GetMapping("/expiry-report")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<BatchExpiryReportDTO> getBatchExpiryReport() {
        return ResponseEntity.ok(batchService.generateBatchExpiryReport());
    }

    /**
     * Mark expired batches manually
     */
    @PostMapping("/mark-expired")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> markExpiredBatches() {
        batchService.markExpiredBatches();
        return ResponseEntity.ok(Map.of("message", "Expired batches have been marked"));
    }
}