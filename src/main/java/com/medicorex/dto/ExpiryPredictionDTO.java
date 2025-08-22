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
public class ExpiryPredictionDTO {
    private LocalDate predictionDate;
    private Integer daysAhead;
    private String algorithm; // LINEAR_REGRESSION, MOVING_AVERAGE, EXPONENTIAL_SMOOTHING

    // Predictions
    private List<PredictionPoint> predictions;

    // Confidence metrics
    private Double overallConfidence;
    private Map<String, Double> categoryConfidence;

    // Risk assessment
    private RiskAssessment riskAssessment;

    // Historical accuracy
    private Double historicalAccuracy;
    private Integer dataPointsUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionPoint {
        private LocalDate date;
        private Integer predictedExpiry;
        private Integer lowerBound;
        private Integer upperBound;
        private Double confidence;
        private BigDecimal estimatedValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private BigDecimal estimatedLoss;
        private List<String> highRiskCategories;
        private List<String> recommendations;
    }
}