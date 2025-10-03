package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLevelComparisonDTO {
    private Long productId;
    private String productName;
    private String productCode;

    // Stock levels
    private Integer currentStock;
    private Integer incomingQuantity;
    private Integer projectedStock;

    // Batch info
    private String batchNumber;
    private Boolean willCreateNewBatch;
    private String existingBatchId;

    // Financial
    private BigDecimal unitCost;
    private BigDecimal totalValue;

    // Thresholds
    private Integer reorderLevel;
    private Integer minStockLevel;
    private String stockStatus; // "LOW", "ADEQUATE", "OVERSTOCKED"
}