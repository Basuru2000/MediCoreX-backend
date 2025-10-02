package com.medicorex.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptRejectDTO {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;

    @Builder.Default  // ADD THIS ANNOTATION
    private Boolean notifySupplier = true;  // Whether to notify supplier
}