package com.medicorex.service.expiry;

import com.medicorex.entity.*;
import com.medicorex.repository.*;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchExpiryTrackingService {

    private final ProductBatchRepository batchRepository;
    private final ExpiryAlertRepository alertRepository;
    private final ExpiryAlertConfigRepository configRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Check batches for expiry and generate alerts
     * This is called by the main expiry monitoring service
     */
    @Transactional
    public void checkBatchExpiry(LocalDate checkDate) {
        log.info("Starting batch expiry check for date: {}", checkDate);

        // Get all active configurations
        List<ExpiryAlertConfig> activeConfigs = configRepository.findByActiveTrue();
        if (activeConfigs.isEmpty()) {
            log.warn("No active expiry alert configurations found");
            return;
        }

        // FIX: Use findAll and filter instead of findByStatus
        List<ProductBatch> activeBatches = batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .collect(Collectors.toList());

        log.info("Found {} active batches to check", activeBatches.size());

        int alertsGenerated = 0;

        for (ProductBatch batch : activeBatches) {
            if (batch.getExpiryDate() == null) {
                continue;
            }

            long daysUntilExpiry = ChronoUnit.DAYS.between(checkDate, batch.getExpiryDate());

            // Check against each configuration
            for (ExpiryAlertConfig config : activeConfigs) {
                if (daysUntilExpiry > 0 && daysUntilExpiry <= config.getDaysBeforeExpiry()) {
                    // Check if alert already exists for this batch
                    boolean alertExists = alertRepository.existsByBatchIdAndConfigId(batch.getId(), config.getId());

                    if (!alertExists) {
                        createBatchAlert(batch, config, checkDate, (int) daysUntilExpiry);
                        alertsGenerated++;

                        // Send notification
                        try {
                            Map<String, String> params = new HashMap<>();
                            params.put("productName", batch.getProduct().getName());
                            params.put("batchNumber", batch.getBatchNumber());
                            params.put("days", String.valueOf(daysUntilExpiry));

                            Map<String, Object> actionData = new HashMap<>();
                            actionData.put("batchId", batch.getId());
                            actionData.put("productId", batch.getProduct().getId());

                            String templateCode;
                            if (daysUntilExpiry <= 7) {
                                templateCode = "EXPIRY_CRITICAL";
                            } else if (daysUntilExpiry <= 15) {
                                templateCode = "EXPIRY_WARNING";
                            } else if (daysUntilExpiry <= 30) {
                                templateCode = "EXPIRY_NOTICE";
                            } else {
                                templateCode = "BATCH_EXPIRING";
                            }

                            notificationService.notifyUsersByRole(
                                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                                    templateCode,
                                    params,
                                    actionData
                            );

                            log.debug("Created notification for expiring batch: {} ({} days until expiry)",
                                    batch.getBatchNumber(), daysUntilExpiry);

                        } catch (Exception e) {
                            log.error("Failed to send expiry notification for batch {}: {}",
                                    batch.getBatchNumber(), e.getMessage());
                        }

                        break; // Only create one alert per batch
                    }
                } else if (daysUntilExpiry <= 0 && !alertExists(batch, "EXPIRED")) {
                    // Batch has expired - create critical notification
                    try {
                        Map<String, String> params = new HashMap<>();
                        params.put("productName", batch.getProduct().getName());
                        params.put("batchNumber", batch.getBatchNumber());

                        Map<String, Object> actionData = new HashMap<>();
                        actionData.put("batchId", batch.getId());
                        actionData.put("productId", batch.getProduct().getId());

                        notificationService.notifyUsersByRole(
                                Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                                "EXPIRED_PRODUCT",
                                params,
                                actionData
                        );

                        log.warn("Batch {} has expired - critical notification sent", batch.getBatchNumber());

                    } catch (Exception e) {
                        log.error("Failed to send expired notification for batch {}: {}",
                                batch.getBatchNumber(), e.getMessage());
                    }
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

            // ===== FIXED NOTIFICATION TRIGGER =====
            try {
                Map<String, String> params = new HashMap<>();
                params.put("productName", batch.getProduct().getName());
                params.put("batchNumber", batch.getBatchNumber());

                Map<String, Object> actionData = new HashMap<>();
                actionData.put("batchId", batch.getId());
                actionData.put("productId", batch.getProduct().getId());
                actionData.put("type", "batch_expired");

                notificationService.notifyUsersByRole(
                        Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                        "BATCH_EXPIRED",
                        params,
                        actionData
                );

                log.info("Sent expired notification for batch {}", batch.getBatchNumber());

            } catch (Exception e) {
                log.error("Failed to send expired batch notification: {}", e.getMessage());
            }
        }

        // Update product quantities
        Set<Long> affectedProductIds = expiredBatches.stream()
                .map(b -> b.getProduct().getId())
                .collect(java.util.stream.Collectors.toSet());

        log.info("Marked {} batches as expired", expiredBatches.size());
    }

    /**
     * Get batch expiry report
     */
    @Transactional(readOnly = true)
    public BatchExpiryReport generateBatchExpiryReport() {
        BatchExpiryReport report = new BatchExpiryReport();
        LocalDate today = LocalDate.now();

        // Get counts by expiry range
        report.setExpiringIn7Days(countBatchesExpiringInDays(7));
        report.setExpiringIn30Days(countBatchesExpiringInDays(30));
        report.setExpiringIn60Days(countBatchesExpiringInDays(60));
        report.setExpiringIn90Days(countBatchesExpiringInDays(90));
        report.setExpiredBatches(countExpiredBatches());

        // Get batch details for critical items (expiring in 7 days)
        LocalDate sevenDaysFromNow = today.plusDays(7);
        List<ProductBatch> criticalBatches = batchRepository.findExpiringBatches(sevenDaysFromNow);
        report.setCriticalBatches(criticalBatches);

        return report;
    }

    // Helper methods
    // FIX: Update createBatchAlert method to set all required fields including expiry_date
    private void createBatchAlert(ProductBatch batch, ExpiryAlertConfig config, LocalDate checkDate, int daysBeforeExpiry) {
        // Validate batch has expiry date
        if (batch.getExpiryDate() == null) {
            log.warn("Cannot create alert for batch {} without expiry date", batch.getBatchNumber());
            return;
        }

        ExpiryAlert alert = new ExpiryAlert();
        alert.setProduct(batch.getProduct());
        alert.setBatch(batch);
        alert.setConfig(config);
        alert.setAlertDate(checkDate);
        alert.setExpiryDate(batch.getExpiryDate()); // Set the required expiry_date field
        alert.setBatchNumber(batch.getBatchNumber()); // Set batch number
        alert.setQuantityAffected(batch.getQuantity()); // Set quantity affected
        alert.setStatus(ExpiryAlert.AlertStatus.PENDING);

        alertRepository.save(alert);

        log.debug("Created expiry alert for batch {} - {} days before expiry",
                batch.getBatchNumber(), daysBeforeExpiry);
    }

    private boolean alertExists(ProductBatch batch, String type) {
        // Check if an alert of this type already exists for this batch
        return alertRepository.existsByBatchId(batch.getId());
    }

    // FIX: Update count methods to use repository queries or manual counting
    private long countBatchesExpiringInDays(int days) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(days);

        // Manual count since repository method doesn't exist
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> !batch.getExpiryDate().isBefore(today))
                .filter(batch -> !batch.getExpiryDate().isAfter(targetDate))
                .count();
    }

    private long countExpiredBatches() {
        LocalDate today = LocalDate.now();

        // Manual count since repository method doesn't exist
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> batch.getExpiryDate().isBefore(today))
                .count();
    }

    // Inner class for report
    public static class BatchExpiryReport {
        private long expiringIn7Days;
        private long expiringIn30Days;
        private long expiringIn60Days;
        private long expiringIn90Days;
        private long expiredBatches;
        private List<ProductBatch> criticalBatches;

        // Getters and setters
        public long getExpiringIn7Days() { return expiringIn7Days; }
        public void setExpiringIn7Days(long expiringIn7Days) { this.expiringIn7Days = expiringIn7Days; }

        public long getExpiringIn30Days() { return expiringIn30Days; }
        public void setExpiringIn30Days(long expiringIn30Days) { this.expiringIn30Days = expiringIn30Days; }

        public long getExpiringIn60Days() { return expiringIn60Days; }
        public void setExpiringIn60Days(long expiringIn60Days) { this.expiringIn60Days = expiringIn60Days; }

        public long getExpiringIn90Days() { return expiringIn90Days; }
        public void setExpiringIn90Days(long expiringIn90Days) { this.expiringIn90Days = expiringIn90Days; }

        public long getExpiredBatches() { return expiredBatches; }
        public void setExpiredBatches(long expiredBatches) { this.expiredBatches = expiredBatches; }

        public List<ProductBatch> getCriticalBatches() { return criticalBatches; }
        public void setCriticalBatches(List<ProductBatch> criticalBatches) { this.criticalBatches = criticalBatches; }
    }
}