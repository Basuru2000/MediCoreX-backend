package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for overall procurement metrics summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementMetricsDTO {

    // Overall counts
    private Long totalPOs;
    private Long pendingApproval;
    private Long approved;
    private Long sent;
    private Long partiallyReceived;
    private Long received;
    private Long cancelled;

    // Financial metrics
    private BigDecimal totalValue;
    private BigDecimal avgPOValue;
    private BigDecimal pendingValue;

    // Performance metrics
    private Double avgApprovalTimeHours;
    private Double fulfillmentRate;

    // Trend indicators
    private Integer posThisMonth;
    private Integer posLastMonth;
    private Double monthlyGrowthPercentage;
}