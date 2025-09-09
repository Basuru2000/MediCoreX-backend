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
public class SupplierPriceComparisonDTO {
    private Long productId;
    private String productName;
    private String productCode;
    private Integer currentStock;
    private List<SupplierPriceOption> supplierOptions;
    private String recommendedSupplierId;
    private String recommendationReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierPriceOption {
        private Long supplierId;
        private String supplierName;
        private String supplierCode;
        private BigDecimal unitPrice;
        private String currency;
        private BigDecimal effectivePrice;
        private Integer leadTimeDays;
        private Integer minOrderQuantity;
        private Boolean isPreferred;
        private BigDecimal rating;
        private BigDecimal totalCost; // For specific quantity
    }
}