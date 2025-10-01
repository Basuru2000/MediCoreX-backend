package com.medicorex.dto;

import com.medicorex.entity.PurchaseOrder.POStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDTO {
    private Long id;
    private String poNumber;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private LocalDateTime orderDate;
    private LocalDate expectedDeliveryDate;
    private POStatus status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String notes;
    private Long createdById;
    private String createdByName;
    private Long approvedById;
    private String approvedByName;
    private LocalDateTime approvedDate;
    private String rejectionComments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PurchaseOrderLineDTO> lines;
    private Integer totalItems;
    private Integer totalReceived;
}