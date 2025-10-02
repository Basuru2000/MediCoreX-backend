package com.medicorex.dto;

import com.medicorex.entity.GoodsReceipt.ReceiptStatus;
import com.medicorex.entity.GoodsReceipt.AcceptanceStatus;  // ✨ ADD THIS
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptDTO {
    private Long id;
    private String receiptNumber;
    private Long poId;
    private String poNumber;
    private LocalDateTime receiptDate;
    private Long receivedById;
    private String receivedByName;
    private Long supplierId;
    private String supplierName;
    private ReceiptStatus status;

    // ✨ NEW FIELDS FOR PHASE 3.2
    private AcceptanceStatus acceptanceStatus;
    private String rejectionReason;
    private Long qualityCheckedById;
    private String qualityCheckedByName;
    private LocalDateTime qualityCheckedAt;
    // ✨ END NEW FIELDS

    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<GoodsReceiptLineDTO> lines;
    private Integer totalQuantity;
}