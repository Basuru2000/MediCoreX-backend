package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatchExpiryReportDTO {
    private Integer totalBatches;
    private Integer activeBatches;
    private Integer expiringBatches;
    private Integer expiredBatches;
    private Integer quarantinedBatches;

    private BigDecimal totalInventoryValue;
    private BigDecimal expiringInventoryValue;
    private BigDecimal expiredInventoryValue;

    private Map<String, List<BatchSummary>> batchesByExpiryRange;
    private List<ProductBatchDTO> criticalBatches; // Expiring in next 7 days

    // ✅ NEW: Timeline-specific data
    private TimelineStatistics timelineStats;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BatchSummary {
        private Long batchId;
        private String productName;
        private String batchNumber;
        private Integer quantity;
        private Integer daysUntilExpiry;
        private BigDecimal value;
    }

    // ✅ NEW: Timeline statistics
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TimelineStatistics {
        private Map<String, TimelineRangeStats> rangeStats;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TimelineRangeStats {
        private String rangeName;
        private Integer batchCount;
        private Integer totalQuantity;
        private BigDecimal totalValue;
        private String severityLevel; // CRITICAL, HIGH, MEDIUM, LOW
        private Integer daysRange; // For sorting: 7, 30, 60, 90, 999 (expired)
    }
}