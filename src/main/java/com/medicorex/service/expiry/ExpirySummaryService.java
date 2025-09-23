package com.medicorex.service.expiry;

import com.medicorex.dto.ExpirySummaryDTO;
import com.medicorex.dto.ExpirySummaryDTO.CriticalItemDTO;
import com.medicorex.dto.ExpirySummaryDTO.TrendIndicator;
import com.medicorex.entity.*;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpirySummaryService {

    private final ProductBatchRepository batchRepository;
    private final ExpiryAlertRepository alertRepository;
    private final QuarantineRecordRepository quarantineRepository;
    private final ExpiryCheckLogRepository checkLogRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Get comprehensive expiry summary for dashboard
     */
    public ExpirySummaryDTO getExpirySummary() {
        log.debug("Generating expiry summary for dashboard");

        LocalDate today = LocalDate.now();
        LocalDate weekFromNow = today.plusDays(7);
        LocalDate monthFromNow = today.plusDays(30);

        ExpirySummaryDTO summary = ExpirySummaryDTO.builder()
                .expiredCount(countExpiredItems(today))
                .expiringTodayCount(countExpiringToday(today))
                .expiringThisWeekCount(countExpiringInPeriod(today, weekFromNow))
                .expiringThisMonthCount(countExpiringInPeriod(today, monthFromNow))
                .severityBreakdown(getSeverityBreakdown())
                .categoryBreakdown(getCategoryBreakdown(today, monthFromNow))
                .totalValueAtRisk(calculateValueAtRisk(today, monthFromNow))
                .expiredValue(calculateExpiredValue(today))
                .criticalItems(getCriticalItems(today))
                .pendingAlertsCount(countAlertsByStatus(ExpiryAlert.AlertStatus.PENDING))
                .acknowledgedAlertsCount(countAlertsByStatus(ExpiryAlert.AlertStatus.ACKNOWLEDGED))
                .resolvedAlertsCount(countAlertsByStatus(ExpiryAlert.AlertStatus.RESOLVED))
                .quarantinedItemsCount(countQuarantinedItems())
                .pendingReviewCount(countPendingReview())
                .lastCheckTime(getLastCheckTime())
                .lastCheckStatus(getLastCheckStatus())
                .expiredTrend(calculateExpiredTrend())
                .alertsTrend(calculateAlertsTrend())
                .build();

        log.debug("Generated expiry summary with {} expired items and {} critical alerts",
                summary.getExpiredCount(), summary.getPendingAlertsCount());

        return summary;
    }

    /**
     * Get critical alerts for quick action
     */
    public List<CriticalItemDTO> getCriticalAlerts(int limit) {
        LocalDate today = LocalDate.now();
        LocalDate criticalDate = today.plusDays(7);

        List<ProductBatch> criticalBatches = batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> !batch.getExpiryDate().isAfter(criticalDate))
                .sorted(Comparator.comparing(ProductBatch::getExpiryDate))
                .limit(limit)
                .collect(Collectors.toList());

        return criticalBatches.stream()
                .map(this::convertToCriticalItem)
                .collect(Collectors.toList());
    }

    // Private helper methods

    private Long countExpiredItems(LocalDate today) {
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> batch.getExpiryDate().isBefore(today))
                .count();
    }

    private Long countExpiringToday(LocalDate today) {
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> batch.getExpiryDate().equals(today))
                .count();
    }

    private Long countExpiringInPeriod(LocalDate start, LocalDate end) {
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> !batch.getExpiryDate().isBefore(start))
                .filter(batch -> !batch.getExpiryDate().isAfter(end))
                .count();
    }

    private Map<String, Long> getSeverityBreakdown() {
        Map<String, Long> breakdown = new HashMap<>();

        // Count alerts by severity
        breakdown.put("CRITICAL", alertRepository.countByStatusAndSeverity(
                ExpiryAlert.AlertStatus.PENDING,
                ExpiryAlertConfig.AlertSeverity.CRITICAL));
        breakdown.put("WARNING", alertRepository.countByStatusAndSeverity(
                ExpiryAlert.AlertStatus.PENDING,
                ExpiryAlertConfig.AlertSeverity.WARNING));
        breakdown.put("INFO", alertRepository.countByStatusAndSeverity(
                ExpiryAlert.AlertStatus.PENDING,
                ExpiryAlertConfig.AlertSeverity.INFO));

        return breakdown;
    }

    private Map<String, Long> getCategoryBreakdown(LocalDate start, LocalDate end) {
        Map<String, Long> breakdown = new HashMap<>();

        List<ProductBatch> expiringBatches = batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> !batch.getExpiryDate().isBefore(start))
                .filter(batch -> !batch.getExpiryDate().isAfter(end))
                .collect(Collectors.toList());

        for (ProductBatch batch : expiringBatches) {
            String categoryName = batch.getProduct().getCategory() != null
                    ? batch.getProduct().getCategory().getName()
                    : "Uncategorized";
            breakdown.merge(categoryName, 1L, Long::sum);
        }

        return breakdown;
    }

    private BigDecimal calculateValueAtRisk(LocalDate start, LocalDate end) {
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> !batch.getExpiryDate().isBefore(start))
                .filter(batch -> !batch.getExpiryDate().isAfter(end))
                .map(batch -> {
                    BigDecimal unitPrice = batch.getProduct().getUnitPrice();
                    if (unitPrice == null) unitPrice = BigDecimal.ZERO;
                    return unitPrice.multiply(BigDecimal.valueOf(batch.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateExpiredValue(LocalDate today) {
        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> batch.getExpiryDate().isBefore(today))
                .map(batch -> {
                    BigDecimal unitPrice = batch.getProduct().getUnitPrice();
                    if (unitPrice == null) unitPrice = BigDecimal.ZERO;
                    return unitPrice.multiply(BigDecimal.valueOf(batch.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<CriticalItemDTO> getCriticalItems(LocalDate today) {
        LocalDate criticalDate = today.plusDays(7);

        return batchRepository.findAll().stream()
                .filter(batch -> batch.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .filter(batch -> batch.getExpiryDate() != null)
                .filter(batch -> !batch.getExpiryDate().isAfter(criticalDate))
                .sorted(Comparator.comparing(ProductBatch::getExpiryDate))
                .limit(5)
                .map(this::convertToCriticalItem)
                .collect(Collectors.toList());
    }

    private CriticalItemDTO convertToCriticalItem(ProductBatch batch) {
        Product product = batch.getProduct();
        LocalDate today = LocalDate.now();
        long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());

        String severity;
        if (daysUntilExpiry <= 0) {
            severity = "EXPIRED";
        } else if (daysUntilExpiry <= 7) {
            severity = "CRITICAL";
        } else if (daysUntilExpiry <= 15) {
            severity = "HIGH";
        } else if (daysUntilExpiry <= 30) {
            severity = "MEDIUM";
        } else {
            severity = "LOW";
        }

        BigDecimal value = product.getUnitPrice() != null
                ? product.getUnitPrice().multiply(BigDecimal.valueOf(batch.getQuantity()))
                : BigDecimal.ZERO;

        return CriticalItemDTO.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productCode(product.getCode())
                .batchNumber(batch.getBatchNumber())
                .expiryDate(batch.getExpiryDate())
                .quantity(batch.getQuantity())
                .value(value)
                .daysUntilExpiry((int) daysUntilExpiry)
                .severity(severity)
                .category(product.getCategory() != null ? product.getCategory().getName() : "Uncategorized")
                .location("Main Warehouse")
                .actionUrl("/batch-tracking/" + batch.getId())
                .build();
    }

    private Long countAlertsByStatus(ExpiryAlert.AlertStatus status) {
        return alertRepository.countByStatus(status);
    }

    private Long countQuarantinedItems() {
        return quarantineRepository.count();
    }

    private Long countPendingReview() {
        return quarantineRepository.countByStatus(QuarantineRecord.QuarantineStatus.PENDING_REVIEW);
    }

    private LocalDateTime getLastCheckTime() {
        return checkLogRepository.findTop30ByOrderByCheckDateDesc().stream()
                .findFirst()
                .map(ExpiryCheckLog::getStartTime)
                .orElse(null);
    }

    private String getLastCheckStatus() {
        return checkLogRepository.findTop30ByOrderByCheckDateDesc().stream()
                .findFirst()
                .map(log -> log.getStatus().toString())
                .orElse("NO_DATA");
    }

    private TrendIndicator calculateExpiredTrend() {
        LocalDate today = LocalDate.now();
        LocalDate lastWeek = today.minusWeeks(1);

        Long currentExpired = countExpiredItems(today);
        Long lastWeekExpired = countExpiredItems(lastWeek);

        double percentageChange = 0;
        String direction = "STABLE";
        String severity = "GOOD";

        if (lastWeekExpired > 0) {
            percentageChange = ((double)(currentExpired - lastWeekExpired) / lastWeekExpired) * 100;

            if (percentageChange > 0) {
                direction = "UP";
                severity = percentageChange > 20 ? "CRITICAL" : "WARNING";
            } else if (percentageChange < 0) {
                direction = "DOWN";
                severity = "GOOD";
            }
        } else if (currentExpired > 0) {
            direction = "UP";
            severity = "WARNING";
            percentageChange = 100;
        }

        String message = String.format("%s %.1f%% from last week",
                direction.equals("UP") ? "Increased" : direction.equals("DOWN") ? "Decreased" : "No change",
                Math.abs(percentageChange));

        return TrendIndicator.builder()
                .percentageChange(percentageChange)
                .direction(direction)
                .severity(severity)
                .message(message)
                .build();
    }

    private TrendIndicator calculateAlertsTrend() {
        // Calculate trend for pending alerts
        Long currentAlerts = countAlertsByStatus(ExpiryAlert.AlertStatus.PENDING);

        // For simplicity, we'll compare with resolved alerts as a baseline
        Long resolvedAlerts = countAlertsByStatus(ExpiryAlert.AlertStatus.RESOLVED);

        double ratio = resolvedAlerts > 0 ? (double)currentAlerts / resolvedAlerts : currentAlerts;

        String direction;
        String severity;
        String message;

        if (ratio > 2) {
            direction = "UP";
            severity = "CRITICAL";
            message = "High number of unresolved alerts";
        } else if (ratio > 1) {
            direction = "UP";
            severity = "WARNING";
            message = "Moderate alert backlog";
        } else {
            direction = "STABLE";
            severity = "GOOD";
            message = "Alerts under control";
        }

        return TrendIndicator.builder()
                .percentageChange(ratio * 100)
                .direction(direction)
                .severity(severity)
                .message(message)
                .build();
    }
}