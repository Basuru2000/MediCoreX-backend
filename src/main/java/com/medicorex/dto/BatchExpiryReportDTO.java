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
}