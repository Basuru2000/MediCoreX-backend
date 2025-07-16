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
public class ProductDTO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Integer quantity;
    private Integer minStockLevel;
    private String unit;
    private BigDecimal unitPrice;
    private LocalDate expiryDate;
    private String batchNumber;
    private String manufacturer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Calculated fields
    private String stockStatus; // LOW, NORMAL, OUT_OF_STOCK
    private Boolean isExpiringSoon;
}