package com.medicorex.service.expiry;

import com.medicorex.entity.*;
import com.medicorex.repository.*;
import com.medicorex.service.NotificationService; // ADD THIS IMPORT
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchExpiryTrackingService {

    private final ProductBatchRepository batchRepository;
    private final ExpiryAlertRepository alertRepository;
    private final ExpiryAlertConfigRepository configRepository;
    private final NotificationService notificationService; // ADD THIS FIELD

    /**
     * Check batches for expiry and generate alerts
     * This is called by the main ExpiryMonitoringService
     */
    @Transactional
    public void checkBatchExpiry(LocalDate checkDate) {
        log.info("Starting batch expiry check for date: {}", checkDate);

        // Get active alert configurations
        List<ExpiryAlertConfig> activeConfigs = configRepository.findByActiveTrueOrderBySortOrderAsc();

        // Get all active batches
        List<ProductBatch> activeBatches = batchRepository.findAll().stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(b -> b.getQuantity() > 0)
                .toList();

        log.info("Checking {} active batches", activeBatches.size());

        int alertsGenerated = 0;

        for (ProductBatch batch : activeBatches) {
            // Calculate days until expiry
            long daysUntilExpiry = ChronoUnit.DAYS.between(checkDate, batch.getExpiryDate());

            // Check against each configuration
            for (ExpiryAlertConfig config : activeConfigs) {
                if (daysUntilExpiry > 0 && daysUntilExpiry <= config.getDaysBeforeExpiry()) {
                    // Check if alert already exists for this batch
                    boolean alertExists = alertRepository.existsByBatchIdAndConfigId(batch.getId(), config.getId());

                    if (!alertExists) {
                        createBatchAlert(batch, config, checkDate);
                        alertsGenerated++;

                        // ============================================
                        // ADD NOTIFICATION HERE
                        // ============================================
                        notificationService.createExpiryAlertNotification(
                                batch.getId(),
                                batch.getProduct().getName(),
                                batch.getBatchNumber(),
                                (int) daysUntilExpiry
                        );
                        log.debug("Created notification for expiring batch: {} ({} days until expiry)",
                                batch.getBatchNumber(), daysUntilExpiry);

                        break; // Only create one alert per batch
                    }
                } else if (daysUntilExpiry <= 0 && !alertExists(batch, "EXPIRED")) {
                    // Batch has expired - create critical notification
                    notificationService.createExpiryAlertNotification(
                            batch.getId(),
                            batch.getProduct().getName(),
                            batch.getBatchNumber(),
                            0  // 0 or negative means expired
                    );
                    log.warn("Batch {} has expired - critical notification sent", batch.getBatchNumber());
                }
            }
        }

        log.info("Generated {} new batch expiry alerts", alertsGenerated);
    }

    /**
     * Daily task to mark expired batches
     * Runs at 3 AM (after the main expiry check at 2 AM)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void markExpiredBatches() {
        log.info("Running daily expired batch marking task");

        LocalDate today = LocalDate.now();
        List<ProductBatch> expiredBatches = batchRepository.findExpiredActiveBatches(today);

        for (ProductBatch batch : expiredBatches) {
            batch.setStatus(ProductBatch.BatchStatus.EXPIRED);
            batchRepository.save(batch);
            log.info("Marked batch {} as expired", batch.getBatchNumber());

            // ============================================
            // ADD EXPIRED NOTIFICATION
            // ============================================
            // Create notification for expired batch
            Map<String, String> params = new HashMap<>();
            params.put("productName", batch.getProduct().getName());
            params.put("batchNumber", batch.getBatchNumber());

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("batchId", batch.getId());
            actionData.put("productId", batch.getProduct().getId());
            actionData.put("type", "batch_expired");

            notificationService.notifyUsersByRole(
                    List.of("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "EXPIRED",
                    params,
                    actionData
            );
        }

        log.info("Marked {} batches as expired", expiredBatches.size());
    }

    /**
     * Create expiry alert for a batch
     */
    private void createBatchAlert(ProductBatch batch, ExpiryAlertConfig config, LocalDate alertDate) {
        ExpiryAlert alert = new ExpiryAlert();
        alert.setProduct(batch.getProduct());
        alert.setConfig(config);
        alert.setBatch(batch);
        alert.setBatchNumber(batch.getBatchNumber());
        alert.setAlertDate(alertDate);
        alert.setExpiryDate(batch.getExpiryDate());
        alert.setQuantityAffected(batch.getQuantity());
        alert.setStatus(ExpiryAlert.AlertStatus.PENDING);

        alertRepository.save(alert);

        log.debug("Created expiry alert for batch {} of product {}",
                batch.getBatchNumber(), batch.getProduct().getName());
    }

    /**
     * Helper method to check if alert exists
     * FIX: Updated to use correct repository method
     */
    private boolean alertExists(ProductBatch batch, String type) {
        // Alternative 1: Check using batch and status
        List<ExpiryAlert> alerts = alertRepository.findByBatchId(batch.getId());
        return !alerts.isEmpty();
    }

    // Alternative: Add this method to check for existing alerts more precisely
    private boolean hasExistingAlert(ProductBatch batch) {
        // This checks if there's already an unresolved alert for this batch
        List<ExpiryAlert> existingAlerts = alertRepository.findByBatchId(batch.getId());
        return existingAlerts.stream()
                .anyMatch(alert -> alert.getStatus() == ExpiryAlert.AlertStatus.PENDING
                        || alert.getStatus() == ExpiryAlert.AlertStatus.ACKNOWLEDGED);
    }
}