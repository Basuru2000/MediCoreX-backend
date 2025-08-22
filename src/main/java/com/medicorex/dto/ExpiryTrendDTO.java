package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryTrendDTO {
    private LocalDate date;
    private Integer totalProducts;
    private Integer expiredCount;
    private Integer expiring7Days;
    private Integer expiring30Days;
    private Integer expiring60Days;
    private Integer expiring90Days;
    private BigDecimal expiredValue;
    private BigDecimal expiring7DaysValue;
    private BigDecimal expiring30DaysValue;
    private Double avgDaysToExpiry;
    private String trendDirection; // IMPROVING, STABLE, WORSENING
    private Double trendPercentage;
    private CategoryTrendDTO criticalCategory;

    // Comparison with previous period
    private Integer expiredCountChange;
    private Double expiredCountChangePercent;
    private BigDecimal valueAtRiskChange;
    private Double valueAtRiskChangePercent;

    // Predictive metrics
    private Integer predicted30DayExpiry;
    private Double predictionConfidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryTrendDTO {
        private Long categoryId;
        private String categoryName;
        private Integer expiringCount;
        private BigDecimal value;
    }
}