package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertGenerationReportDTO {
    private Integer totalProductsProcessed;
    private Integer totalAlertsGenerated;
    private Integer duplicatesSkipped;
    private Integer errorsEncountered;
    private List<ProductAlertSummary> productAlerts;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductAlertSummary {
        private Long productId;
        private String productName;
        private String productCode;
        private String batchNumber;
        private Integer daysUntilExpiry;
        private String tierName;
        private String severity;
        private Boolean isNewAlert;
    }
}