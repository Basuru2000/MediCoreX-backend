package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "metric_month", nullable = false)
    private LocalDate metricMonth;

    // Delivery Performance Metrics
    @Column(name = "total_deliveries")
    private Integer totalDeliveries = 0;

    @Column(name = "on_time_deliveries")
    private Integer onTimeDeliveries = 0;

    @Column(name = "late_deliveries")
    private Integer lateDeliveries = 0;

    @Column(name = "early_deliveries")
    private Integer earlyDeliveries = 0;

    @Column(name = "delivery_performance_score", precision = 5, scale = 2)
    private BigDecimal deliveryPerformanceScore = BigDecimal.ZERO;

    // Quality Metrics
    @Column(name = "total_items_received")
    private Integer totalItemsReceived = 0;

    @Column(name = "accepted_items")
    private Integer acceptedItems = 0;

    @Column(name = "rejected_items")
    private Integer rejectedItems = 0;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore = BigDecimal.ZERO;

    // Pricing Metrics
    @Column(name = "average_price_variance", precision = 5, scale = 2)
    private BigDecimal averagePriceVariance = BigDecimal.ZERO;

    @Column(name = "total_spend", precision = 12, scale = 2)
    private BigDecimal totalSpend = BigDecimal.ZERO;

    @Column(name = "cost_savings", precision = 12, scale = 2)
    private BigDecimal costSavings = BigDecimal.ZERO;

    // Response Time Metrics
    @Column(name = "average_response_time_hours")
    private Integer averageResponseTimeHours = 0;

    @Column(name = "average_lead_time_days")
    private Integer averageLeadTimeDays = 0;

    // Compliance Metrics
    @Column(name = "compliance_score", precision = 5, scale = 2)
    private BigDecimal complianceScore = BigDecimal.valueOf(100);

    @Column(name = "documentation_accuracy", precision = 5, scale = 2)
    private BigDecimal documentationAccuracy = BigDecimal.valueOf(100);

    // Overall Performance
    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore = BigDecimal.ZERO;

    @Column(name = "performance_trend")
    @Enumerated(EnumType.STRING)
    private PerformanceTrend performanceTrend = PerformanceTrend.STABLE;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        calculateOverallScore();
    }

    @PrePersist
    public void prePersist() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
        calculateOverallScore();
    }

    public void calculateOverallScore() {
        // Weighted average calculation
        BigDecimal deliveryWeight = new BigDecimal("0.30");
        BigDecimal qualityWeight = new BigDecimal("0.35");
        BigDecimal complianceWeight = new BigDecimal("0.20");
        BigDecimal priceWeight = new BigDecimal("0.15");

        BigDecimal weightedScore = BigDecimal.ZERO;

        if (deliveryPerformanceScore != null) {
            weightedScore = weightedScore.add(deliveryPerformanceScore.multiply(deliveryWeight));
        }

        if (qualityScore != null) {
            weightedScore = weightedScore.add(qualityScore.multiply(qualityWeight));
        }

        if (complianceScore != null) {
            weightedScore = weightedScore.add(complianceScore.multiply(complianceWeight));
        }

        // Price score (inverse of variance, capped at 100)
        BigDecimal priceScore = BigDecimal.valueOf(100).subtract(
                averagePriceVariance != null ? averagePriceVariance.abs() : BigDecimal.ZERO
        );
        if (priceScore.compareTo(BigDecimal.ZERO) < 0) {
            priceScore = BigDecimal.ZERO;
        }
        weightedScore = weightedScore.add(priceScore.multiply(priceWeight));

        this.overallScore = weightedScore.setScale(2, BigDecimal.ROUND_HALF_UP);

        // Update supplier rating
        if (supplier != null && overallScore != null) {
            supplier.setRating(overallScore.divide(BigDecimal.valueOf(20), 2, BigDecimal.ROUND_HALF_UP));
        }
    }

    public enum PerformanceTrend {
        IMPROVING,
        STABLE,
        DECLINING
    }
}