package com.medicorex.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    @NotNull(message = "Transaction type is required")
    private String type; // "PURCHASE", "SALE", "ADJUSTMENT", "DAMAGE", "RETURN", "TRANSFER"

    private String reference; // Reference number for the transaction

    private String notes; // Additional notes for the adjustment

    private String reason; // Reason for adjustment (especially for damage/return)
}