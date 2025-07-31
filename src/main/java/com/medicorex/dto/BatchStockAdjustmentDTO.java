package com.medicorex.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatchStockAdjustmentDTO {

    @NotNull(message = "Batch ID is required")
    private Long batchId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotBlank(message = "Adjustment type is required")
    private String adjustmentType; // "CONSUME", "ADD", "ADJUST", "QUARANTINE"

    @NotBlank(message = "Reason is required")
    private String reason;

    private String referenceType; // "SALE", "PRESCRIPTION", "TRANSFER", etc.
    private Long referenceId;
}