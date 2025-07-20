package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockValuationDTO {
    // Summary metrics
    private BigDecimal totalInventoryValue;
    private Long totalProducts;
    private Long totalCategories;
    private Long totalQuantity;

    // Stock status breakdown
    private Map<String, Long> stockStatusCount;
    private Map<String, BigDecimal> stockStatusValue;

    // Expiry analysis
    private Long expiringProductsCount;
    private BigDecimal expiringProductsValue;

    // Low stock analysis
    private Long lowStockProductsCount;
    private BigDecimal lowStockProductsValue;

    // Category breakdown
    private List<CategoryValuationDTO> categoryValuations;

    // Top products by value
    private List<ProductValuationDTO> topProductsByValue;

    // Timestamp
    private LocalDateTime generatedAt;
}