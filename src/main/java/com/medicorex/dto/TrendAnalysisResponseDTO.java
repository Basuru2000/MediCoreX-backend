package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendAnalysisResponseDTO {
    // Period information
    private LocalDate startDate;
    private LocalDate endDate;
    private String granularity; // DAILY, WEEKLY, MONTHLY

    // Trend data points
    private List<ExpiryTrendDataPointDTO> trendData;

    // Summary statistics
    private SummaryStatistics summary;

    // Category breakdown
    private Map<String, CategoryAnalysis> categoryAnalysis;

    // Insights and recommendations
    private List<TrendInsight> insights;

    // Predictions
    private ExpiryPredictionDTO predictions; // For including predictions in response

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStatistics {
        private Integer totalExpired;
        private Integer totalExpiring;
        private BigDecimal totalValueLost;
        private BigDecimal totalValueAtRisk;
        private Double averageExpiryRate;
        private String overallTrend;
        private Double trendStrength; // 0-100
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAnalysis {
        private String categoryName;
        private Integer expiryCount;
        private BigDecimal value;
        private Double percentageOfTotal;
        private String trend;
        private List<String> topProducts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendInsight {
        private String type; // WARNING, INFO, SUCCESS
        private String title;
        private String description;
        private String recommendation;
        private Double severity; // 0-100
    }
}