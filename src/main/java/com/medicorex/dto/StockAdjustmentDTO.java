package com.medicorex.dto;

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
public class StockAdjustmentDTO {
    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    private Integer quantity; // Positive for addition, negative for reduction

    @NotBlank(message = "Adjustment type is required")
    private String type; // PURCHASE, SALE, ADJUSTMENT, DAMAGE, EXPIRY

    @NotBlank(message = "Reason is required")
    private String reason;

    private String reference; // Reference number (e.g., PO number, invoice number)
}