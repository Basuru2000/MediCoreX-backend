package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for PO status distribution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POStatusDistributionDTO {

    private String status;
    private Long count;
    private BigDecimal totalValue;
    private BigDecimal avgValue;
    private Double percentage;  // Calculated percentage of total POs
}