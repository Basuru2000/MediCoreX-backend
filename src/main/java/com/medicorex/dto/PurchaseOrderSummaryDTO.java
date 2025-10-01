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
public class PurchaseOrderSummaryDTO {
    private Long totalOrders;
    private Long draftOrders;
    private Long approvedOrders;
    private Long sentOrders;
    private Long receivedOrders;
    private Long cancelledOrders;
    private BigDecimal totalValue;
    private BigDecimal pendingValue;
}