package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierMetricsDTO {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private LocalDate metricMonth;

    // Delivery Metrics
    private Integer totalDeliveries;
    private Integer onTimeDeliveries;
    private Integer lateDeliveries;
    private BigDecimal deliveryPerformanceScore;

    // Quality Metrics
    private Integer totalItemsReceived;
    private Integer acceptedItems;
    private Integer rejectedItems;
    private BigDecimal qualityScore;

    // Pricing Metrics
    private BigDecimal averagePriceVariance;
    private BigDecimal totalSpend;
    private BigDecimal costSavings;

    // Compliance
    private BigDecimal complianceScore;

    // Overall Performance
    private BigDecimal overallScore;
    private String performanceTrend;
    private LocalDateTime calculatedAt;
}