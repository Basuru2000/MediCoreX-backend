package com.medicorex.service.expiry;

import com.medicorex.dto.AlertGenerationReportDTO;
import com.medicorex.dto.AlertGenerationReportDTO.ProductAlertSummary;
import com.medicorex.entity.*;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryAlertGenerator {

    private final ProductRepository productRepository;
    private final ExpiryAlertConfigRepository configRepository;
    private final ExpiryAlertRepository alertRepository;
    private final ExpiryCheckLogRepository checkLogRepository;

    /**
     * Generate alerts for all products based on active configurations
     */
    @Transactional
    public AlertGenerationReportDTO generateAlertsForDate(LocalDate checkDate, Long checkLogId) {
        log.info("Generating expiry alerts for date: {}", checkDate);

        // Get active configurations
        List<ExpiryAlertConfig> activeConfigs = configRepository.findByActiveTrueOrderBySortOrderAsc();
        if (activeConfigs.isEmpty()) {
            log.warn("No active expiry alert configurations found");
            return buildEmptyReport();
        }

        // Get all products with expiry dates
        List<Product> productsWithExpiry = productRepository.findAll().stream()
                .filter(p -> p.getExpiryDate() != null && p.getQuantity() > 0)
                .collect(Collectors.toList());

        log.info("Processing {} products with expiry dates", productsWithExpiry.size());

        // Process each product
        AlertGenerationReportDTO report = new AlertGenerationReportDTO();
        report.setTotalProductsProcessed(productsWithExpiry.size());
        report.setProductAlerts(new ArrayList<>());

        int alertsGenerated = 0;
        int duplicatesSkipped = 0;
        int errors = 0;

        for (Product product : productsWithExpiry) {
            try {
                ProcessResult result = processProductExpiry(product, activeConfigs, checkDate, checkLogId);
                alertsGenerated += result.alertsCreated;
                duplicatesSkipped += result.duplicatesSkipped;

                // Add to report
                result.summaries.forEach(summary -> report.getProductAlerts().add(summary));

            } catch (Exception e) {
                log.error("Error processing product {}: {}", product.getId(), e.getMessage());
                errors++;
            }
        }

        report.setTotalAlertsGenerated(alertsGenerated);
        report.setDuplicatesSkipped(duplicatesSkipped);
        report.setErrorsEncountered(errors);

        log.info("Alert generation completed. Alerts: {}, Duplicates: {}, Errors: {}",
                alertsGenerated, duplicatesSkipped, errors);

        return report;
    }

    /**
     * Process expiry for a single product
     */
    private ProcessResult processProductExpiry(Product product, List<ExpiryAlertConfig> configs,
                                               LocalDate checkDate, Long checkLogId) {
        ProcessResult result = new ProcessResult();

        // Calculate days until expiry
        long daysUntilExpiry = ChronoUnit.DAYS.between(checkDate, product.getExpiryDate());

        // Skip if product has already expired
        if (daysUntilExpiry < 0) {
            log.debug("Product {} has already expired", product.getId());
            return result;
        }

        // Check against each configuration
        for (ExpiryAlertConfig config : configs) {
            if (daysUntilExpiry <= config.getDaysBeforeExpiry()) {
                // Check if alert already exists
                List<ExpiryAlert> existingAlerts = alertRepository.findPendingAlerts(
                        product.getId(), config.getId()
                );

                if (!existingAlerts.isEmpty()) {
                    log.debug("Alert already exists for product {} and config {}",
                            product.getId(), config.getId());
                    result.duplicatesSkipped++;

                    // Add to report as existing alert
                    result.summaries.add(createAlertSummary(product, config, daysUntilExpiry, false));
                } else {
                    // Create new alert
                    ExpiryAlert alert = createAlert(product, config, checkDate, checkLogId);
                    alertRepository.save(alert);
                    result.alertsCreated++;

                    log.debug("Created alert for product {} with config {}",
                            product.getId(), config.getTierName());

                    // Add to report as new alert
                    result.summaries.add(createAlertSummary(product, config, daysUntilExpiry, true));
                }

                // Only create one alert per product (highest priority config)
                break;
            }
        }

        return result;
    }

    /**
     * Create new expiry alert
     */
    private ExpiryAlert createAlert(Product product, ExpiryAlertConfig config,
                                    LocalDate alertDate, Long checkLogId) {
        ExpiryAlert alert = new ExpiryAlert();
        alert.setProduct(product);
        alert.setConfig(config);
        alert.setBatchNumber(product.getBatchNumber());
        alert.setAlertDate(alertDate);
        alert.setExpiryDate(product.getExpiryDate());
        alert.setQuantityAffected(product.getQuantity());
        alert.setStatus(ExpiryAlert.AlertStatus.PENDING);

        // Link to check log
        if (checkLogId != null) {
            ExpiryCheckLog checkLog = new ExpiryCheckLog();
            checkLog.setId(checkLogId);
            // Note: In real implementation, you'd add this field to ExpiryAlert entity
            // For now, we'll store it in notes
            alert.setNotes("Generated by check log: " + checkLogId);
        }

        return alert;
    }

    /**
     * Create alert summary for report
     */
    private ProductAlertSummary createAlertSummary(Product product, ExpiryAlertConfig config,
                                                   long daysUntilExpiry, boolean isNewAlert) {
        return ProductAlertSummary.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productCode(product.getCode())
                .batchNumber(product.getBatchNumber())
                .daysUntilExpiry((int) daysUntilExpiry)
                .tierName(config.getTierName())
                .severity(config.getSeverity().toString())
                .isNewAlert(isNewAlert)
                .build();
    }

    /**
     * Build empty report
     */
    private AlertGenerationReportDTO buildEmptyReport() {
        AlertGenerationReportDTO report = new AlertGenerationReportDTO();
        report.setTotalProductsProcessed(0);
        report.setTotalAlertsGenerated(0);
        report.setDuplicatesSkipped(0);
        report.setErrorsEncountered(0);
        report.setProductAlerts(new ArrayList<>());
        return report;
    }

    /**
     * Internal class for processing results
     */
    private static class ProcessResult {
        int alertsCreated = 0;
        int duplicatesSkipped = 0;
        List<ProductAlertSummary> summaries = new ArrayList<>();
    }
}