package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptLineDTO {
    private Long id;
    private Long receiptId;
    private Long poLineId;
    private Long productId;
    private String productName;
    private String productCode;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private Long batchId;
    private String batchNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
    private String qualityNotes;
}