package com.medicorex.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderCreateDTO {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    private LocalDate expectedDeliveryDate;

    private BigDecimal taxAmount;

    private BigDecimal discountAmount;

    private String notes;

    @NotNull(message = "At least one line item is required")
    @Size(min = 1, message = "At least one line item is required")
    @Valid
    private List<PurchaseOrderLineCreateDTO> lines;
}