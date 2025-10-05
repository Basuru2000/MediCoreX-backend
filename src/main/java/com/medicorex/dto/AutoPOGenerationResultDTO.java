package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPOGenerationResultDTO {
    private Boolean success;
    private Integer productsEvaluated;
    private Integer lowStockProducts;
    private Integer posGenerated;
    private BigDecimal totalValue;

    @Builder.Default  // ← FIX: Add this annotation
    private List<String> generatedPoNumbers = new ArrayList<>();

    @Builder.Default  // ← FIX: Add this annotation
    private List<String> warnings = new ArrayList<>();

    @Builder.Default  // ← FIX: Add this annotation
    private List<String> errors = new ArrayList<>();

    private String executionTime;
}