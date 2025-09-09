package com.medicorex.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProductCreateDTO {
    @NotNull(message = "Product ID is required")
    private Long productId;

    private String supplierProductCode;
    private String supplierProductName;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
    private BigDecimal unitPrice;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter code")
    private String currency = "USD";

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    @DecimalMax(value = "100", message = "Discount cannot exceed 100%")
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "Bulk discount cannot be negative")
    @DecimalMax(value = "100", message = "Bulk discount cannot exceed 100%")
    private BigDecimal bulkDiscountPercentage = BigDecimal.ZERO;

    @Min(value = 0, message = "Bulk quantity threshold cannot be negative")
    private Integer bulkQuantityThreshold = 0;

    @Min(value = 0, message = "Lead time cannot be negative")
    private Integer leadTimeDays = 0;

    @Min(value = 1, message = "Minimum order quantity must be at least 1")
    private Integer minOrderQuantity = 1;

    private Integer maxOrderQuantity;
    private Boolean isPreferred = false;
    private Boolean isActive = true;
    private String notes;
}