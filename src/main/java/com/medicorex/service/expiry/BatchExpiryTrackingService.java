package com.medicorex.service.expiry;

import com.medicorex.entity.*;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchExpiryTrackingService {

    private final ProductBatchRepository batchRepository;
    private final ExpiryAlertRepository alertRepository;
    private final ExpiryAlertConfigRepository configRepository;
    private final ProductBatchService batchService;

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
                        break; // Only create one alert per batch
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
        batchService.markExpiredBatches();
    }

    /**
     * Create expiry alert for a batch
     */
    private void createBatchAlert(ProductBatch batch, ExpiryAlertConfig config, LocalDate alertDate) {
        ExpiryAlert alert = new ExpiryAlert();
        alert.setProduct(batch.getProduct());
        alert.setConfig(config);
        alert.setBatchNumber(batch.getBatchNumber());
        alert.setAlertDate(alertDate);
        alert.setExpiryDate(batch.getExpiryDate());
        alert.setQuantityAffected(batch.getQuantity());
        alert.setStatus(ExpiryAlert.AlertStatus.PENDING);

        // Set the batch reference (if we added batch_id to expiry_alerts table)
        // For now, we'll include it in the notes
        alert.setNotes("Batch ID: " + batch.getId() + ", Batch: " + batch.getBatchNumber());

        alertRepository.save(alert);

        log.debug("Created expiry alert for batch {} of product {}",
                batch.getBatchNumber(), batch.getProduct().getName());
    }
}