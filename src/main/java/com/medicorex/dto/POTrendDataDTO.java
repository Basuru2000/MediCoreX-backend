package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for monthly PO trend data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POTrendDataDTO {

    private String month;           // Format: "2025-01"
    private String monthLabel;      // Format: "Jan 2025"
    private Long poCount;
    private BigDecimal totalValue;
    private BigDecimal avgPoValue;
    private Long approvedCount;
    private Long receivedCount;
    private Long cancelledCount;
    private Double avgApprovalTimeHours;
}