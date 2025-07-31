package com.medicorex.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductBatchCreateDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotBlank(message = "Batch number is required")
    @Size(max = 50, message = "Batch number must not exceed 50 characters")
    private String batchNumber;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;

    @PastOrPresent(message = "Manufacture date cannot be in the future")
    private LocalDate manufactureDate;

    @Size(max = 100, message = "Supplier reference must not exceed 100 characters")
    private String supplierReference;

    @DecimalMin(value = "0.00", message = "Cost per unit must be positive")
    @Digits(integer = 8, fraction = 2, message = "Cost per unit format is invalid")
    private BigDecimal costPerUnit;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}