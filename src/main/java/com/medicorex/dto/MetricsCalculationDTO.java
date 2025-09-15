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
public class MetricsCalculationDTO {
    private LocalDate calculationDate;
    private Integer totalSuppliers;
    private BigDecimal averageDeliveryScore;
    private BigDecimal averageQualityScore;
    private BigDecimal averageOverallScore;
    private Integer improvingCount;
    private Integer stableCount;
    private Integer decliningCount;
}