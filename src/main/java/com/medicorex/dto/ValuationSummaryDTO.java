package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValuationSummaryDTO {
    private String label;
    private String description;
    private BigDecimal value;
    private Long count;
    private String trend; // UP, DOWN, STABLE
    private BigDecimal percentageChange;
    private String color; // For UI display
    private String icon; // Icon identifier for UI
}