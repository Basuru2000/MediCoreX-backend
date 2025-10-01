package com.medicorex.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLineCreateDTO {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Unit price is required")
    @Min(value = 0, message = "Unit price must be positive")
    private BigDecimal unitPrice;

    private BigDecimal discountPercentage;

    private BigDecimal taxPercentage;

    private String notes;
}