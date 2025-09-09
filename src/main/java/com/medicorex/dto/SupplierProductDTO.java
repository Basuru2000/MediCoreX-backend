package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProductDTO {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private Long productId;
    private String productName;
    private String productCode;
    private String supplierProductCode;
    private String supplierProductName;
    private BigDecimal unitPrice;
    private String currency;
    private BigDecimal discountPercentage;
    private BigDecimal bulkDiscountPercentage;
    private Integer bulkQuantityThreshold;
    private Integer leadTimeDays;
    private Integer minOrderQuantity;
    private Integer maxOrderQuantity;
    private Boolean isPreferred;
    private Boolean isActive;
    private LocalDate lastPriceUpdate;
    private String notes;
    private BigDecimal effectivePrice; // Calculated field
}