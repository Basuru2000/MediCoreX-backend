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
public class SupplierPerformanceDTO {
    private Long supplierId;
    private String supplierName;
    private BigDecimal overallScore;
    private BigDecimal deliveryScore;
    private BigDecimal qualityScore;
    private BigDecimal complianceScore;
    private String trend;
    private Integer rank;
}