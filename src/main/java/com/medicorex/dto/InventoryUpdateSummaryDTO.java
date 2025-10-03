package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateSummaryDTO {

    // Overall summary
    private Integer totalItemsReceived;
    private Integer uniqueProducts;
    private Integer batchesCreated;
    private Integer batchesUpdated;
    private BigDecimal totalValue;

    // Per-product breakdown
    private List<ProductInventoryUpdate> productUpdates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInventoryUpdate {
        private Long productId;
        private String productName;
        private String productCode;
        private Integer quantityBefore;
        private Integer quantityAfter;
        private Integer quantityAdded;
        private String batchNumber;
        private Boolean batchCreated; // true if new batch, false if updated existing
        private BigDecimal unitCost;
        private BigDecimal lineTotal;
    }
}