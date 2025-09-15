package com.medicorex.service.supplier;

import com.medicorex.dto.*;
import com.medicorex.entity.Supplier;
import com.medicorex.entity.SupplierMetrics;
import com.medicorex.entity.SupplierMetrics.PerformanceTrend;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.SupplierMetricsRepository;
import com.medicorex.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierMetricsService {

    private final SupplierMetricsRepository metricsRepository;
    private final SupplierRepository supplierRepository;

    /**
     * Get current month metrics for a supplier
     */
    public SupplierMetricsDTO getCurrentMetrics(Long supplierId) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        return getMetricsForMonth(supplierId, currentMonth);
    }

    /**
     * Get metrics for a specific month
     */
    public SupplierMetricsDTO getMetricsForMonth(Long supplierId, LocalDate month) {
        LocalDate firstDayOfMonth = month.withDayOfMonth(1);

        SupplierMetrics metrics = metricsRepository
                .findBySupplierIdAndMetricMonth(supplierId, firstDayOfMonth)
                .orElseGet(() -> initializeMetrics(supplierId, firstDayOfMonth));

        return convertToDTO(metrics);
    }

    /**
     * Get historical metrics for a supplier
     */
    public List<SupplierMetricsDTO> getHistoricalMetrics(Long supplierId, int months) {
        LocalDate endDate = LocalDate.now().withDayOfMonth(1);
        LocalDate startDate = endDate.minusMonths(months);

        List<SupplierMetrics> metrics = metricsRepository
                .findBySupplierIdAndDateRange(supplierId, startDate, endDate);

        return metrics.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get performance comparison across suppliers
     */
    public List<SupplierPerformanceDTO> getSupplierComparison(LocalDate month) {
        LocalDate firstDayOfMonth = month != null ? month.withDayOfMonth(1) :
                LocalDate.now().withDayOfMonth(1);

        List<SupplierMetrics> allMetrics = metricsRepository
                .findTopPerformersForMonth(firstDayOfMonth, PageRequest.of(0, 100));

        return allMetrics.stream()
                .map(this::convertToPerformanceDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update delivery metrics
     */
    public void updateDeliveryMetrics(Long supplierId, boolean onTime) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        SupplierMetrics metrics = getOrCreateMetrics(supplierId, currentMonth);

        metrics.setTotalDeliveries(metrics.getTotalDeliveries() + 1);

        if (onTime) {
            metrics.setOnTimeDeliveries(metrics.getOnTimeDeliveries() + 1);
        } else {
            metrics.setLateDeliveries(metrics.getLateDeliveries() + 1);
        }

        // Recalculate delivery performance score
        if (metrics.getTotalDeliveries() > 0) {
            BigDecimal score = BigDecimal.valueOf(metrics.getOnTimeDeliveries())
                    .divide(BigDecimal.valueOf(metrics.getTotalDeliveries()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            metrics.setDeliveryPerformanceScore(score);
        }

        updatePerformanceTrend(metrics);
        metricsRepository.save(metrics);
        log.info("Updated delivery metrics for supplier {}: onTime={}", supplierId, onTime);
    }

    /**
     * Update quality metrics
     */
    public void updateQualityMetrics(Long supplierId, int itemsReceived, int itemsAccepted) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        SupplierMetrics metrics = getOrCreateMetrics(supplierId, currentMonth);

        metrics.setTotalItemsReceived(metrics.getTotalItemsReceived() + itemsReceived);
        metrics.setAcceptedItems(metrics.getAcceptedItems() + itemsAccepted);
        metrics.setRejectedItems(metrics.getRejectedItems() + (itemsReceived - itemsAccepted));

        // Recalculate quality score
        if (metrics.getTotalItemsReceived() > 0) {
            BigDecimal score = BigDecimal.valueOf(metrics.getAcceptedItems())
                    .divide(BigDecimal.valueOf(metrics.getTotalItemsReceived()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            metrics.setQualityScore(score);
        }

        updatePerformanceTrend(metrics);
        metricsRepository.save(metrics);
        log.info("Updated quality metrics for supplier {}: accepted={}/{}",
                supplierId, itemsAccepted, itemsReceived);
    }

    /**
     * Update pricing metrics
     */
    public void updatePricingMetrics(Long supplierId, BigDecimal orderAmount, BigDecimal marketPrice) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        SupplierMetrics metrics = getOrCreateMetrics(supplierId, currentMonth);

        metrics.setTotalSpend(metrics.getTotalSpend().add(orderAmount));

        // Calculate price variance as percentage
        if (marketPrice != null && marketPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal variance = orderAmount.subtract(marketPrice)
                    .divide(marketPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Update average price variance
            BigDecimal currentVariance = metrics.getAveragePriceVariance();
            if (currentVariance == null) {
                metrics.setAveragePriceVariance(variance);
            } else {
                // Simple moving average
                metrics.setAveragePriceVariance(
                        currentVariance.add(variance).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                );
            }

            // Calculate savings if price is below market
            if (variance.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal savings = marketPrice.subtract(orderAmount);
                metrics.setCostSavings(metrics.getCostSavings().add(savings));
            }
        }

        metricsRepository.save(metrics);
        log.info("Updated pricing metrics for supplier {}: spend={}", supplierId, orderAmount);
    }

    /**
     * Calculate and update metrics for all suppliers
     */
    @Scheduled(cron = "0 0 2 1 * ?") // Run at 2 AM on the 1st of each month
    public void calculateMonthlyMetrics() {
        log.info("Starting monthly metrics calculation for all suppliers");

        LocalDate lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        List<Supplier> activeSuppliers = supplierRepository.findByStatus(Supplier.SupplierStatus.ACTIVE);

        for (Supplier supplier : activeSuppliers) {
            try {
                calculateSupplierMetrics(supplier.getId(), lastMonth);
            } catch (Exception e) {
                log.error("Failed to calculate metrics for supplier {}: {}",
                        supplier.getName(), e.getMessage());
            }
        }

        log.info("Completed monthly metrics calculation for {} suppliers", activeSuppliers.size());
    }

    /**
     * Manually trigger metrics calculation for a supplier
     */
    public SupplierMetricsDTO calculateSupplierMetrics(Long supplierId, LocalDate month) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        LocalDate firstDayOfMonth = month.withDayOfMonth(1);
        SupplierMetrics metrics = getOrCreateMetrics(supplierId, firstDayOfMonth);

        // In a real implementation, these would be calculated from actual order/delivery data
        // For now, we'll ensure the metrics are properly initialized
        metrics.setCalculatedAt(LocalDateTime.now());

        // Calculate overall score and trend
        metrics.calculateOverallScore();
        updatePerformanceTrend(metrics);

        SupplierMetrics saved = metricsRepository.save(metrics);
        log.info("Calculated metrics for supplier {} for month {}", supplier.getName(), month);

        return convertToDTO(saved);
    }

    /**
     * Get metrics summary for dashboard
     */
    public MetricsCalculationDTO getMetricsSummary() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

        List<SupplierMetrics> currentMetrics = metricsRepository
                .findTopPerformersForMonth(currentMonth, PageRequest.of(0, 10));

        // Calculate averages
        BigDecimal avgDeliveryScore = currentMetrics.stream()
                .map(SupplierMetrics::getDeliveryPerformanceScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(currentMetrics.size(), 1)), 2, RoundingMode.HALF_UP);

        BigDecimal avgQualityScore = currentMetrics.stream()
                .map(SupplierMetrics::getQualityScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(currentMetrics.size(), 1)), 2, RoundingMode.HALF_UP);

        BigDecimal avgOverallScore = currentMetrics.stream()
                .map(SupplierMetrics::getOverallScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(currentMetrics.size(), 1)), 2, RoundingMode.HALF_UP);

        // Count trends
        Map<PerformanceTrend, Long> trendCounts = currentMetrics.stream()
                .collect(Collectors.groupingBy(
                        SupplierMetrics::getPerformanceTrend,
                        Collectors.counting()
                ));

        return MetricsCalculationDTO.builder()
                .calculationDate(currentMonth)
                .totalSuppliers(currentMetrics.size())
                .averageDeliveryScore(avgDeliveryScore)
                .averageQualityScore(avgQualityScore)
                .averageOverallScore(avgOverallScore)
                .improvingCount(trendCounts.getOrDefault(PerformanceTrend.IMPROVING, 0L).intValue())
                .stableCount(trendCounts.getOrDefault(PerformanceTrend.STABLE, 0L).intValue())
                .decliningCount(trendCounts.getOrDefault(PerformanceTrend.DECLINING, 0L).intValue())
                .build();
    }

    /**
     * Helper Methods
     */
    private SupplierMetrics getOrCreateMetrics(Long supplierId, LocalDate month) {
        return metricsRepository
                .findBySupplierIdAndMetricMonth(supplierId, month)
                .orElseGet(() -> initializeMetrics(supplierId, month));
    }

    private SupplierMetrics initializeMetrics(Long supplierId, LocalDate month) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        SupplierMetrics metrics = new SupplierMetrics();
        metrics.setSupplier(supplier);
        metrics.setMetricMonth(month);
        metrics.setCalculatedAt(LocalDateTime.now());

        return metricsRepository.save(metrics);
    }

    private void updatePerformanceTrend(SupplierMetrics current) {
        // Get previous month's metrics
        LocalDate previousMonth = current.getMetricMonth().minusMonths(1);
        Optional<SupplierMetrics> previous = metricsRepository
                .findBySupplierIdAndMetricMonth(current.getSupplier().getId(), previousMonth);

        if (previous.isPresent() && previous.get().getOverallScore() != null &&
                current.getOverallScore() != null) {
            BigDecimal diff = current.getOverallScore().subtract(previous.get().getOverallScore());

            if (diff.compareTo(BigDecimal.valueOf(5)) > 0) {
                current.setPerformanceTrend(PerformanceTrend.IMPROVING);
            } else if (diff.compareTo(BigDecimal.valueOf(-5)) < 0) {
                current.setPerformanceTrend(PerformanceTrend.DECLINING);
            } else {
                current.setPerformanceTrend(PerformanceTrend.STABLE);
            }
        }
    }

    private SupplierMetricsDTO convertToDTO(SupplierMetrics metrics) {
        return SupplierMetricsDTO.builder()
                .id(metrics.getId())
                .supplierId(metrics.getSupplier().getId())
                .supplierName(metrics.getSupplier().getName())
                .supplierCode(metrics.getSupplier().getCode())
                .metricMonth(metrics.getMetricMonth())
                .totalDeliveries(metrics.getTotalDeliveries())
                .onTimeDeliveries(metrics.getOnTimeDeliveries())
                .lateDeliveries(metrics.getLateDeliveries())
                .deliveryPerformanceScore(metrics.getDeliveryPerformanceScore())
                .totalItemsReceived(metrics.getTotalItemsReceived())
                .acceptedItems(metrics.getAcceptedItems())
                .rejectedItems(metrics.getRejectedItems())
                .qualityScore(metrics.getQualityScore())
                .averagePriceVariance(metrics.getAveragePriceVariance())
                .totalSpend(metrics.getTotalSpend())
                .costSavings(metrics.getCostSavings())
                .complianceScore(metrics.getComplianceScore())
                .overallScore(metrics.getOverallScore())
                .performanceTrend(metrics.getPerformanceTrend().toString())
                .calculatedAt(metrics.getCalculatedAt())
                .build();
    }

    private SupplierPerformanceDTO convertToPerformanceDTO(SupplierMetrics metrics) {
        return SupplierPerformanceDTO.builder()
                .supplierId(metrics.getSupplier().getId())
                .supplierName(metrics.getSupplier().getName())
                .overallScore(metrics.getOverallScore())
                .deliveryScore(metrics.getDeliveryPerformanceScore())
                .qualityScore(metrics.getQualityScore())
                .complianceScore(metrics.getComplianceScore())
                .trend(metrics.getPerformanceTrend().toString())
                .rank(0) // Will be set by the controller based on sorting
                .build();
    }
}