package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expiry_trend_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryTrendSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private Integer totalProducts;

    @Column(nullable = false)
    private Integer expiredCount;

    private Integer expiring7Days;
    private Integer expiring30Days;
    private Integer expiring60Days;
    private Integer expiring90Days;

    @Column(precision = 15, scale = 2)
    private BigDecimal expiredValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal expiring7DaysValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal expiring30DaysValue;

    @Column(precision = 10)
    private Double avgDaysToExpiry;

    private Long criticalCategoryId;
    private String criticalCategoryName;
    private Integer criticalCategoryCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TrendDirection trendDirection;

    @Column(precision = 5)
    private Double trendPercentage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TrendDirection {
        IMPROVING,
        STABLE,
        WORSENING
    }
}