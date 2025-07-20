package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryValuationDTO {
    private Long categoryId;
    private String categoryName;
    private Long productCount;
    private Long totalQuantity;
    private BigDecimal totalValue;
    private BigDecimal percentageOfTotal;
    private BigDecimal averageProductValue;
}