package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpirySummaryDTO {

    // Critical counts
    private Long expiredCount;
    private Long expiringTodayCount;
    private Long expiringThisWeekCount;
    private Long expiringThisMonthCount;

    // Detailed breakdown by severity
    private Map<String, Long> severityBreakdown;

    // Category breakdown
    private Map<String, Long> categoryBreakdown;

    // Financial impact
    private BigDecimal totalValueAtRisk;
    private BigDecimal expiredValue;

    // Recent critical items (top 5)
    private List<CriticalItemDTO> criticalItems;

    // Alert statistics
    private Long pendingAlertsCount;
    private Long acknowledgedAlertsCount;
    private Long resolvedAlertsCount;

    // Quarantine statistics
    private Long quarantinedItemsCount;
    private Long pendingReviewCount;

    // Last check information
    private LocalDateTime lastCheckTime;
    private String lastCheckStatus;

    // Trend indicators (compared to last week)
    private TrendIndicator expiredTrend;
    private TrendIndicator alertsTrend;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CriticalItemDTO {
        private Long productId;
        private String productName;
        private String productCode;
        private String batchNumber;
        private LocalDate expiryDate;
        private Integer quantity;
        private BigDecimal value;
        private Integer daysUntilExpiry;
        private String severity;
        private String category;
        private String location;
        private String actionUrl;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendIndicator {
        private Double percentageChange;
        private String direction; // UP, DOWN, STABLE
        private String severity; // GOOD, WARNING, CRITICAL
        private String message;
    }
}