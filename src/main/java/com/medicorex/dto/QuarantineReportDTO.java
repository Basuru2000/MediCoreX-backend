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
public class QuarantineReportDTO {

    // Report metadata
    private String reportId;
    private LocalDate reportDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String generatedBy;

    // Summary statistics
    private SummaryStats summary;

    // Detailed breakdowns
    private List<ProductBreakdown> productBreakdown;
    private List<ReasonBreakdown> reasonBreakdown;
    private List<StatusBreakdown> statusBreakdown;
    private List<MonthlyTrend> monthlyTrends;

    // Financial impact
    private FinancialImpact financialImpact;

    // Top items
    private List<TopQuarantinedItem> topQuarantinedItems;
    private List<PendingAction> pendingActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryStats {
        private Integer totalRecords;
        private Integer totalQuantity;
        private BigDecimal totalValue;
        private BigDecimal totalLoss;
        private Integer itemsDisposed;
        private Integer itemsReturned;
        private Integer itemsPending;
        private Double averageResolutionDays;
        private Double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductBreakdown {
        private Long productId;
        private String productName;
        private String productCode;
        private Integer recordCount;
        private Integer totalQuantity;
        private BigDecimal totalValue;
        private String primaryReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonBreakdown {
        private String reason;
        private Integer count;
        private Integer quantity;
        private BigDecimal value;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusBreakdown {
        private String status;
        private Integer count;
        private Integer quantity;
        private BigDecimal value;
        private Double averageDaysInStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrend {
        private String month;
        private Integer year;
        private Integer recordsCreated;
        private Integer recordsResolved;
        private BigDecimal lossAmount;
        private Double resolutionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialImpact {
        private BigDecimal totalInventoryValue;
        private BigDecimal quarantinedValue;
        private BigDecimal actualLoss;
        private BigDecimal recoveredValue;
        private BigDecimal projectedLoss;
        private Double lossPercentage;
        private Map<String, BigDecimal> lossByCategory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopQuarantinedItem {
        private Long productId;
        private String productName;
        private Integer frequency;
        private Integer totalQuantity;
        private BigDecimal totalValue;
        private String commonReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingAction {
        private Long recordId;
        private String productName;
        private String batchNumber;
        private Integer daysInQuarantine;
        private String currentStatus;
        private String requiredAction;
        private String assignedTo;
    }
}