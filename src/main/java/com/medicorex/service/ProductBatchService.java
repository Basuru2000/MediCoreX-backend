package com.medicorex.service;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.exception.InsufficientBatchStockException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import com.medicorex.service.quarantine.QuarantineService;
import com.medicorex.service.NotificationService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    private QuarantineService quarantineService;

    @Autowired
    private NotificationService notificationService;

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

        // ===== NOTIFICATION TRIGGERS =====
        try {
            // 1. Send batch created notification
            Map<String, String> params = new HashMap<>();
            params.put("batchNumber", savedBatch.getBatchNumber());
            params.put("productName", product.getName());

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("batchId", savedBatch.getId());
            actionData.put("productId", product.getId());

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "BATCH_CREATED",
                    params,
                    actionData
            );

            // 2. Check if batch is expiring soon
            long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), dto.getExpiryDate());

            if (daysUntilExpiry <= 7) {
                // Critical expiry
                params.put("days", String.valueOf(daysUntilExpiry));
                String templateCode = daysUntilExpiry <= 0 ? "BATCH_EXPIRED" : "EXPIRY_CRITICAL";
                notificationService.notifyUsersByRole(
                        Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                        templateCode,
                        params,
                        actionData
                );
            } else if (daysUntilExpiry <= 30) {
                // Warning expiry
                params.put("days", String.valueOf(daysUntilExpiry));
                notificationService.notifyUsersByRole(
                        Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                        "BATCH_EXPIRING",
                        params,
                        actionData
                );
            }

            log.info("Notifications sent for new batch: {}", savedBatch.getBatchNumber());

        } catch (Exception e) {
            log.error("Failed to send batch creation notifications: {}", e.getMessage());
            // Don't fail the batch creation for notification errors
        }

        log.info("Created new batch {} for product {}", batch.getBatchNumber(), product.getName());

        return convertToDTO(savedBatch);
    }

    /**
     * Create a new batch or update existing batch quantity
     * Used during goods receipt to handle multiple deliveries of same batch
     */
    public ProductBatch createOrUpdateBatch(ProductBatchCreateDTO dto) {
        log.info("Processing batch: {} for product: {}", dto.getBatchNumber(), dto.getProductId());

        // Validate product exists
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", dto.getProductId()));

        // Check if batch already exists for this product
        Optional<ProductBatch> existingBatch = batchRepository.findByProductIdAndBatchNumber(
                dto.getProductId(),
                dto.getBatchNumber()
        );

        if (existingBatch.isPresent()) {
            // Update existing batch
            ProductBatch batch = existingBatch.get();

            log.info("Batch {} already exists. Current quantity: {}, Adding: {}",
                    dto.getBatchNumber(), batch.getQuantity(), dto.getQuantity());

            // Add to existing quantity
            batch.setQuantity(batch.getQuantity() + dto.getQuantity());

            // Update cost per unit if provided (use weighted average)
            if (dto.getCostPerUnit() != null && dto.getCostPerUnit().compareTo(BigDecimal.ZERO) > 0) {
                batch.setCostPerUnit(dto.getCostPerUnit());
            }

            // Update other fields if provided
            if (dto.getSupplierReference() != null) {
                batch.setSupplierReference(dto.getSupplierReference());
            }

            if (dto.getNotes() != null) {
                String updatedNotes = batch.getNotes() != null
                        ? batch.getNotes() + " | " + dto.getNotes()
                        : dto.getNotes();
                batch.setNotes(updatedNotes);
            }

            ProductBatch updatedBatch = batchRepository.save(batch);

            // Update product total quantity
            updateProductTotalQuantity(product.getId());

            log.info("✅ Updated batch {} | New quantity: {}",
                    batch.getBatchNumber(), updatedBatch.getQuantity());

            return updatedBatch;

        } else {
            // Create new batch
            log.info("Creating new batch: {}", dto.getBatchNumber());

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

            // Send batch created notification
            try {
                Map<String, String> params = new HashMap<>();
                params.put("batchNumber", savedBatch.getBatchNumber());
                params.put("productName", product.getName());
                params.put("quantity", String.valueOf(savedBatch.getQuantity()));
                params.put("expiryDate", savedBatch.getExpiryDate().toString());

                Map<String, Object> actionData = new HashMap<>();
                actionData.put("batchId", savedBatch.getId());
                actionData.put("productId", product.getId());

                List<User.UserRole> roles = Arrays.asList(
                        User.UserRole.HOSPITAL_MANAGER,
                        User.UserRole.PHARMACY_STAFF
                );
                List<User> recipients = userRepository.findByRoleIn(roles);

                for (User recipient : recipients) {
                    notificationService.createNotificationFromTemplate(
                            recipient.getId(),
                            "BATCH_CREATED",
                            params,
                            actionData
                    );
                }
            } catch (Exception e) {
                log.error("Failed to send batch created notification: {}", e.getMessage());
            }

            log.info("✅ Created new batch {} | Quantity: {}",
                    savedBatch.getBatchNumber(), savedBatch.getQuantity());

            return savedBatch;
        }
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

                // Send notification when batch is depleted
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("batchNumber", batch.getBatchNumber());
                    params.put("productName", batch.getProduct().getName());

                    Map<String, Object> actionData = new HashMap<>();
                    actionData.put("batchId", batch.getId());
                    actionData.put("productId", productId);

                    notificationService.notifyUsersByRole(
                            Arrays.asList("PHARMACY_STAFF", "HOSPITAL_MANAGER"),
                            "BATCH_DEPLETED",
                            params,
                            actionData
                    );
                } catch (Exception e) {
                    log.error("Failed to send batch depletion notification: {}", e.getMessage());
                }
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

        // Handle different adjustment types
        switch (dto.getAdjustmentType().toUpperCase()) {
            case "ADD":
            case "INCREASE":
                batch.setQuantity(batch.getQuantity() + dto.getQuantity());
                break;

            case "CONSUME":
            case "DECREASE":
                if (batch.getQuantity() < dto.getQuantity()) {
                    throw new InsufficientBatchStockException(
                            dto.getBatchId(), dto.getQuantity(), batch.getQuantity());
                }
                batch.setQuantity(batch.getQuantity() - dto.getQuantity());
                break;

            case "SET":
            case "ADJUST":
                batch.setQuantity(dto.getQuantity());
                break;

            case "QUARANTINE":
                String reason = dto.getReason() != null
                        ? dto.getReason()
                        : "Manual quarantine from stock adjustment";

                // Get the current user from security context
                String performedBy = getCurrentUsername();

                quarantineService.quarantineBatch(
                        batch.getId(),
                        reason,
                        performedBy
                );

                // Return the updated batch after quarantine
                return convertToDTO(batchRepository.findById(batch.getId()).orElseThrow());

            default:
                throw new IllegalArgumentException("Invalid adjustment type: " + dto.getAdjustmentType());
        }

        // Update batch status if needed
        if (batch.getQuantity() == 0 && batch.getStatus() == ProductBatch.BatchStatus.ACTIVE) {
            batch.setStatus(ProductBatch.BatchStatus.DEPLETED);
        } else if (batch.getQuantity() > 0 && batch.getStatus() == ProductBatch.BatchStatus.DEPLETED) {
            batch.setStatus(ProductBatch.BatchStatus.ACTIVE);
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

            // Send expired notification
            try {
                Map<String, String> params = new HashMap<>();
                params.put("productName", batch.getProduct().getName());
                params.put("batchNumber", batch.getBatchNumber());

                Map<String, Object> actionData = new HashMap<>();
                actionData.put("batchId", batch.getId());
                actionData.put("productId", batch.getProduct().getId());

                notificationService.notifyUsersByRole(
                        Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                        "BATCH_EXPIRED",
                        params,
                        actionData
                );
            } catch (Exception e) {
                log.error("Failed to send expired batch notification: {}", e.getMessage());
            }

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
        LocalDate cutoffDate = LocalDate.now().plusDays(daysAhead);
        return batchRepository.findExpiringBatches(cutoffDate).stream()
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

        // Existing counts
        long totalBatches = allBatches.size();
        long activeBatches = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .count();
        long quarantinedBatches = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.QUARANTINED)
                .count();

        LocalDate thirtyDaysFromNow = today.plusDays(30);
        long expiringBatches = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> b.getExpiryDate().isAfter(today) && b.getExpiryDate().isBefore(thirtyDaysFromNow))
                .count();

        long expiredBatches = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.EXPIRED ||
                        (b.getStatus() == ProductBatch.BatchStatus.ACTIVE &&
                                b.getExpiryDate() != null &&
                                b.getExpiryDate().isBefore(today)))
                .count();

        // Calculate inventory values
        BigDecimal totalInventoryValue = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(b -> b.getCostPerUnit() != null)
                .map(b -> b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expiringInventoryValue = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> b.getExpiryDate().isAfter(today) && b.getExpiryDate().isBefore(thirtyDaysFromNow))
                .filter(b -> b.getCostPerUnit() != null)
                .map(b -> b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expiredInventoryValue = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.EXPIRED)
                .filter(b -> b.getCostPerUnit() != null)
                .map(b -> b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get critical batches (expiring in next 7 days)
        LocalDate sevenDaysFromNow = today.plusDays(7);
        List<ProductBatchDTO> criticalBatches = allBatches.stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> b.getExpiryDate().isAfter(today) && b.getExpiryDate().isBefore(sevenDaysFromNow))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // ✅ NEW: Generate batches by expiry range with timeline stats
        Map<String, List<BatchExpiryReportDTO.BatchSummary>> batchesByExpiryRange = new LinkedHashMap<>();
        Map<String, BatchExpiryReportDTO.TimelineRangeStats> rangeStatsMap = new LinkedHashMap<>();

        // Define ranges
        LocalDate[] rangeDates = {
                today.plusDays(7),
                today.plusDays(30),
                today.plusDays(60),
                today.plusDays(90)
        };
        String[] rangeNames = {"0-7 days", "8-30 days", "31-60 days", "61-90 days", "Expired"};
        String[] severityLevels = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "CRITICAL"};
        int[] daysRanges = {7, 30, 60, 90, 999};

        for (int i = 0; i < rangeNames.length; i++) {
            String rangeName = rangeNames[i];
            List<BatchExpiryReportDTO.BatchSummary> rangeBatches = new ArrayList<>();

            List<ProductBatch> filteredBatches;
            if (rangeName.equals("Expired")) {
                filteredBatches = allBatches.stream()
                        .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                        .filter(b -> b.getExpiryDate() != null)
                        .filter(b -> b.getExpiryDate().isBefore(today))
                        .collect(Collectors.toList());
            } else {
                LocalDate startDate = (i == 0) ? today : rangeDates[i - 1];
                LocalDate endDate = rangeDates[i];

                filteredBatches = allBatches.stream()
                        .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                        .filter(b -> b.getExpiryDate() != null)
                        .filter(b -> b.getExpiryDate().isAfter(startDate) && b.getExpiryDate().isBefore(endDate))
                        .collect(Collectors.toList());
            }

            int totalQuantity = 0;
            BigDecimal totalValue = BigDecimal.ZERO;

            for (ProductBatch batch : filteredBatches) {
                long daysUntil = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
                BigDecimal value = batch.getCostPerUnit() != null
                        ? batch.getCostPerUnit().multiply(BigDecimal.valueOf(batch.getQuantity()))
                        : BigDecimal.ZERO;

                rangeBatches.add(BatchExpiryReportDTO.BatchSummary.builder()
                        .batchId(batch.getId())
                        .productName(batch.getProduct().getName())
                        .batchNumber(batch.getBatchNumber())
                        .quantity(batch.getQuantity())
                        .daysUntilExpiry((int) daysUntil)
                        .value(value)
                        .build());

                totalQuantity += batch.getQuantity();
                totalValue = totalValue.add(value);
            }

            batchesByExpiryRange.put(rangeName, rangeBatches);

            // Create range stats
            rangeStatsMap.put(rangeName, BatchExpiryReportDTO.TimelineRangeStats.builder()
                    .rangeName(rangeName)
                    .batchCount(rangeBatches.size())
                    .totalQuantity(totalQuantity)
                    .totalValue(totalValue)
                    .severityLevel(severityLevels[i])
                    .daysRange(daysRanges[i])
                    .build());
        }

        BatchExpiryReportDTO.TimelineStatistics timelineStats = BatchExpiryReportDTO.TimelineStatistics.builder()
                .rangeStats(rangeStatsMap)
                .build();

        return BatchExpiryReportDTO.builder()
                .totalBatches((int) totalBatches)
                .activeBatches((int) activeBatches)
                .expiringBatches((int) expiringBatches)
                .expiredBatches((int) expiredBatches)
                .quarantinedBatches((int) quarantinedBatches)
                .totalInventoryValue(totalInventoryValue)
                .expiringInventoryValue(expiringInventoryValue)
                .expiredInventoryValue(expiredInventoryValue)
                .criticalBatches(criticalBatches)
                .batchesByExpiryRange(batchesByExpiryRange)
                .timelineStats(timelineStats)  // ✅ NEW
                .build();
    }

    // Helper methods

    private void updateProductTotalQuantity(Long productId) {
        Integer totalQuantity = batchRepository.getTotalQuantityByProductId(productId);
        Product product = productRepository.findById(productId).orElseThrow();
        product.setQuantity(totalQuantity != null ? totalQuantity : 0);
        productRepository.save(product);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SYSTEM";
    }

    private ProductBatchDTO convertToDTO(ProductBatch batch) {
        LocalDate today = LocalDate.now();
        long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());

        BigDecimal totalValue = batch.getCostPerUnit() != null
                ? batch.getCostPerUnit().multiply(BigDecimal.valueOf(batch.getQuantity()))
                : BigDecimal.ZERO;

        double utilization = batch.getInitialQuantity() > 0
                ? ((double) (batch.getInitialQuantity() - batch.getQuantity()) / batch.getInitialQuantity()) * 100
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
                .status(batch.getStatus().name())
                .notes(batch.getNotes())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .daysUntilExpiry((int) daysUntilExpiry)
                .totalValue(totalValue)
                .utilizationPercentage(utilization)
                .isExpired(daysUntilExpiry <= 0)
                .build();
    }

    @Data
    @Builder
    public static class BatchConsumptionResult {
        private Long batchId;
        private String batchNumber;
        private Integer quantityConsumed;
        private Integer remainingQuantity;
    }
}


// ========================================
// IMPORTANT: The pattern is always:
// ========================================
// 1. Services pass List<String> with role names
// 2. NotificationService.notifyUsersByRole() accepts List<String>
// 3. NotificationService internally converts String to UserRole enum
// 4. UserRepository.findByRoleIn() accepts List<UserRole> enum

// DO NOT pass UserRole enums from services to NotificationService
// DO NOT pass strings directly to UserRepository methods