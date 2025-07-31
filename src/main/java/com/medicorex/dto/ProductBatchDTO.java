package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductBatchDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productCode;
    private String batchNumber;
    private Integer quantity;
    private Integer initialQuantity;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
    private String supplierReference;
    private BigDecimal costPerUnit;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Calculated fields
    private Integer daysUntilExpiry;
    private BigDecimal totalValue;
    private Double utilizationPercentage; // (initialQuantity - quantity) / initialQuantity * 100
}