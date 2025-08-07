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
public class QuarantineSummaryDTO {
    private Long totalItems;
    private Long pendingReview;
    private Long underReview;
    private Long awaitingDisposal;
    private Long awaitingReturn;
    private Long disposed;
    private Long returned;
    private Integer totalQuantity;
    private BigDecimal totalEstimatedLoss;
}