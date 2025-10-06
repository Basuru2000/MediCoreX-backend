package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for top supplier analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopSupplierDTO {

    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private Long totalPOs;
    private BigDecimal totalValue;
    private BigDecimal avgPOValue;
    private Long completedPOs;
    private Long cancelledPOs;
    private Double completionRate;
    private Double avgApprovalTimeHours;
    private LocalDate lastOrderDate;
}