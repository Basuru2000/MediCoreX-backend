package com.medicorex.service;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.exception.InsufficientBatchStockException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import com.medicorex.service.quarantine.QuarantineService; // ✅ ADD THIS IMPORT
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductBatchService {

    private final ProductBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final QuarantineService quarantineService; // ADD THIS if missing

    /**
     * Create a new batch for a product
     */
    public ProductBatchDTO createBatch(ProductBatchCreateDTO dto) {
        // Validate product exists
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", dto.getProductId()));

        // Check if batch number already exists for this product
        if (batchRepository.existsByProductIdAndBatchNumber(dto.getProductId(), dto.getBatchNumber())) {
            throw new IllegalArgumentException(
                    "Batch number '" + dto.getBatchNumber() + "' already exists for this product"
            );
        }

        // Create new batch
        ProductBatch batch = new ProductBatch();
        batch.setProduct(product);
        batch.setBatchNumber(dto.getBatchNumber());
        batch.setQuantity(dto.getQuantity());
        batch.setInitialQuantity(dto.getQuantity());
        batch.setExpiryDate(dto.getExpiryDate());
        batch.setManufactureDate(dto.getManufactureDate());
        batch.setSupplierReference(dto.getSupplierReference());
        batch.setCostPerUnit(dto.getCostPerUnit());
        batch.setNotes(dto.getNotes());
        batch.setStatus(ProductBatch.BatchStatus.ACTIVE);

        ProductBatch savedBatch = batchRepository.save(batch);

        // Update product total quantity
        updateProductTotalQuantity(product.getId());

        log.info("Created new batch {} for product {}", batch.getBatchNumber(), product.getName());

        return convertToDTO(savedBatch);
    }

    /**
     * Get all batches for a product
     */
    @Transactional(readOnly = true)
    public List<ProductBatchDTO> getBatchesByProduct(Long productId) {
        return batchRepository.findByProductIdOrderByExpiryDateAsc(productId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get batch by ID
     */
    @Transactional(readOnly = true)
    public ProductBatchDTO getBatchById(Long batchId) {
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", batchId));
        return convertToDTO(batch);
    }

    /**
     * Consume stock from batches (FIFO)
     */
    public List<BatchConsumptionResult> consumeStock(Long productId, Integer quantity, String reason) {
        List<BatchConsumptionResult> results = new ArrayList<>();

        // Get active batches in FIFO order (by expiry date)
        List<ProductBatch> activeBatches = batchRepository
                .findByProductIdAndStatusOrderByExpiryDateAsc(productId, ProductBatch.BatchStatus.ACTIVE);

        if (activeBatches.isEmpty()) {
            throw new InsufficientBatchStockException(null, quantity, 0);
        }

        // Check total available quantity
        int totalAvailable = activeBatches.stream()
                .mapToInt(ProductBatch::getQuantity)
                .sum();

        if (totalAvailable < quantity) {
            throw new InsufficientBatchStockException(null, quantity, totalAvailable);
        }

        // Consume from batches in FIFO order
        int remainingQuantity = quantity;
        for (ProductBatch batch : activeBatches) {
            if (remainingQuantity <= 0) break;

            int consumeFromBatch = Math.min(batch.getQuantity(), remainingQuantity);
            batch.setQuantity(batch.getQuantity() - consumeFromBatch);

            // Update batch status if depleted
            if (batch.getQuantity() == 0) {
                batch.setStatus(ProductBatch.BatchStatus.DEPLETED);
            }

            batchRepository.save(batch);

            results.add(BatchConsumptionResult.builder()
                    .batchId(batch.getId())
                    .batchNumber(batch.getBatchNumber())
                    .quantityConsumed(consumeFromBatch)
                    .remainingQuantity(batch.getQuantity())
                    .build());

            remainingQuantity -= consumeFromBatch;

            log.info("Consumed {} units from batch {}", consumeFromBatch, batch.getBatchNumber());
        }

        // Update product total quantity
        updateProductTotalQuantity(productId);

        return results;
    }

    /**
     * Adjust batch stock
     */
    public ProductBatchDTO adjustBatchStock(BatchStockAdjustmentDTO dto) {
        ProductBatch batch = batchRepository.findById(dto.getBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", dto.getBatchId()));

        switch (dto.getAdjustmentType().toUpperCase()) {
            case "ADD":
                batch.setQuantity(batch.getQuantity() + dto.getQuantity());
                batch.setStatus(ProductBatch.BatchStatus.ACTIVE);
                break;

            case "CONSUME":
                if (batch.getQuantity() < dto.getQuantity()) {
                    throw new InsufficientBatchStockException(
                            batch.getId(), dto.getQuantity(), batch.getQuantity()
                    );
                }
                batch.setQuantity(batch.getQuantity() - dto.getQuantity());
                if (batch.getQuantity() == 0) {
                    batch.setStatus(ProductBatch.BatchStatus.DEPLETED);
                }
                break;

            case "ADJUST":
                batch.setQuantity(dto.getQuantity());
                if (batch.getQuantity() == 0) {
                    batch.setStatus(ProductBatch.BatchStatus.DEPLETED);
                } else {
                    batch.setStatus(ProductBatch.BatchStatus.ACTIVE);
                }
                break;

            case "QUARANTINE":
                // ✅ FIXED: Now calls quarantine service
                String reason = dto.getReason() != null ? dto.getReason() : "Manual quarantine from stock adjustment";

                // Create quarantine record (this also updates batch status)
                quarantineService.quarantineBatch(
                        batch.getId(),
                        reason,
                        "admin" // TODO: Get from security context
                );

                // Return the updated batch
                return convertToDTO(batchRepository.findById(batch.getId()).orElseThrow());

            default:
                throw new IllegalArgumentException("Invalid adjustment type: " + dto.getAdjustmentType());
        }

        ProductBatch updatedBatch = batchRepository.save(batch);

        // Update product total quantity
        updateProductTotalQuantity(batch.getProduct().getId());

        log.info("Adjusted batch {} stock: {} {}",
                batch.getBatchNumber(), dto.getAdjustmentType(), dto.getQuantity());

        return convertToDTO(updatedBatch);
    }

    /**
     * Mark expired batches
     */
    public void markExpiredBatches() {
        LocalDate today = LocalDate.now();
        List<ProductBatch> expiredBatches = batchRepository.findExpiredActiveBatches(today);

        for (ProductBatch batch : expiredBatches) {
            batch.setStatus(ProductBatch.BatchStatus.EXPIRED);
            batchRepository.save(batch);

            log.info("Marked batch {} as expired", batch.getBatchNumber());
        }

        // Update product quantities
        Set<Long> affectedProductIds = expiredBatches.stream()
                .map(b -> b.getProduct().getId())
                .collect(Collectors.toSet());

        affectedProductIds.forEach(this::updateProductTotalQuantity);
    }

    /**
     * Get batches expiring within specified days
     */
    @Transactional(readOnly = true)
    public List<ProductBatchDTO> getExpiringBatches(int daysAhead) {
        LocalDate endDate = LocalDate.now().plusDays(daysAhead);
        return batchRepository.findBatchesExpiringBetween(LocalDate.now(), endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Generate batch expiry report
     */
    @Transactional(readOnly = true)
    public BatchExpiryReportDTO generateBatchExpiryReport() {
        List<ProductBatch> allBatches = batchRepository.findAll();
        LocalDate today = LocalDate.now();

        // Count batches by status
        Map<ProductBatch.BatchStatus, Long> statusCounts = allBatches.stream()
                .collect(Collectors.groupingBy(ProductBatch::getStatus, Collectors.counting()));

        // Calculate values
        BigDecimal totalValue = calculateTotalValue(allBatches);
        BigDecimal expiringValue = calculateTotalValue(
                allBatches.stream()
                        .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                        .filter(b -> b.getExpiryDate().isBefore(today.plusDays(30)))
                        .collect(Collectors.toList())
        );
        BigDecimal expiredValue = calculateTotalValue(
                allBatches.stream()
                        .filter(b -> b.getStatus() == ProductBatch.BatchStatus.EXPIRED)
                        .collect(Collectors.toList())
        );

        // Group batches by expiry range
        Map<String, List<BatchExpiryReportDTO.BatchSummary>> batchesByRange = new HashMap<>();
        batchesByRange.put("Critical (0-7 days)", getBatchSummaries(allBatches, 0, 7));
        batchesByRange.put("Warning (8-30 days)", getBatchSummaries(allBatches, 8, 30));
        batchesByRange.put("Caution (31-60 days)", getBatchSummaries(allBatches, 31, 60));
        batchesByRange.put("Normal (61-90 days)", getBatchSummaries(allBatches, 61, 90));
        batchesByRange.put("Long-term (90+ days)", getBatchSummaries(allBatches, 91, Integer.MAX_VALUE));

        // Get critical batches
        List<ProductBatchDTO> criticalBatches = getExpiringBatches(7);

        return BatchExpiryReportDTO.builder()
                .totalBatches(allBatches.size())
                .activeBatches(statusCounts.getOrDefault(ProductBatch.BatchStatus.ACTIVE, 0L).intValue())
                .expiringBatches(expiringValue.intValue())
                .expiredBatches(statusCounts.getOrDefault(ProductBatch.BatchStatus.EXPIRED, 0L).intValue())
                .quarantinedBatches(statusCounts.getOrDefault(ProductBatch.BatchStatus.QUARANTINED, 0L).intValue())
                .totalInventoryValue(totalValue)
                .expiringInventoryValue(expiringValue)
                .expiredInventoryValue(expiredValue)
                .batchesByExpiryRange(batchesByRange)
                .criticalBatches(criticalBatches)
                .build();
    }

    // Helper methods

    private void updateProductTotalQuantity(Long productId) {
        Integer totalQuantity = batchRepository.getTotalQuantityByProductId(productId);
        Product product = productRepository.findById(productId).orElseThrow();
        product.setQuantity(totalQuantity);
        productRepository.save(product);
    }

    private ProductBatchDTO convertToDTO(ProductBatch batch) {
        LocalDate today = LocalDate.now();
        long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());

        BigDecimal totalValue = batch.getCostPerUnit() != null
                ? batch.getCostPerUnit().multiply(BigDecimal.valueOf(batch.getQuantity()))
                : BigDecimal.ZERO;

        double utilization = batch.getInitialQuantity() > 0
                ? (double)(batch.getInitialQuantity() - batch.getQuantity()) / batch.getInitialQuantity() * 100
                : 0;

        return ProductBatchDTO.builder()
                .id(batch.getId())
                .productId(batch.getProduct().getId())
                .productName(batch.getProduct().getName())
                .productCode(batch.getProduct().getCode())
                .batchNumber(batch.getBatchNumber())
                .quantity(batch.getQuantity())
                .initialQuantity(batch.getInitialQuantity())
                .expiryDate(batch.getExpiryDate())
                .manufactureDate(batch.getManufactureDate())
                .supplierReference(batch.getSupplierReference())
                .costPerUnit(batch.getCostPerUnit())
                .status(batch.getStatus().toString())
                .notes(batch.getNotes())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .daysUntilExpiry((int) daysUntilExpiry)
                .totalValue(totalValue)
                .utilizationPercentage(utilization)
                .build();
    }

    private BigDecimal calculateTotalValue(List<ProductBatch> batches) {
        return batches.stream()
                .filter(b -> b.getCostPerUnit() != null)
                .map(b -> b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<BatchExpiryReportDTO.BatchSummary> getBatchSummaries(
            List<ProductBatch> batches, int minDays, int maxDays) {
        LocalDate today = LocalDate.now();

        return batches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(b -> {
                    long days = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                    return days >= minDays && days <= maxDays;
                })
                .map(b -> {
                    long daysUntilExpiry = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                    BigDecimal value = b.getCostPerUnit() != null
                            ? b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity()))
                            : BigDecimal.ZERO;

                    return BatchExpiryReportDTO.BatchSummary.builder()
                            .batchId(b.getId())
                            .productName(b.getProduct().getName())
                            .batchNumber(b.getBatchNumber())
                            .quantity(b.getQuantity())
                            .daysUntilExpiry((int) daysUntilExpiry)
                            .value(value)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Result class for batch consumption
     */
    @Data
    @Builder
    public static class BatchConsumptionResult {
        private Long batchId;
        private String batchNumber;
        private Integer quantityConsumed;
        private Integer remainingQuantity;
    }
}