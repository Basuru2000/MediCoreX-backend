package com.medicorex.dto;

import com.medicorex.entity.PurchaseOrder.POStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryDTO {
    private Long id;
    private Long poId;
    private String poNumber;
    private POStatus oldStatus;
    private POStatus newStatus;
    private Long changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
    private String comments;

    // Computed field for display
    private String statusChangeDescription;
}