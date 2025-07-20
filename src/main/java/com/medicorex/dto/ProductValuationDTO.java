package com.medicorex.dto;

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
public class ProductValuationDTO {
    private Long id;
    private String name;
    private String code;
    private String categoryName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalValue;
    private String stockStatus;
    private LocalDate expiryDate;
    private Boolean isExpiringSoon;
    private BigDecimal percentageOfTotalValue;
}