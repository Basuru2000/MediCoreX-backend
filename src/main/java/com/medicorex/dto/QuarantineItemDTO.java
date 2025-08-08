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
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineItemDTO {
    private Long id;
    private Long batchId;
    private String batchNumber;
    private Long productId;
    private String productName;
    private String productCode;
    private Integer quantityQuarantined;
    private String reason;
    private LocalDate quarantineDate;
    private String quarantinedBy;
    private String status;
    private LocalDateTime reviewDate;
    private String reviewedBy;
    private LocalDateTime disposalDate;
    private String disposalMethod;
    private String disposalCertificate;
    private LocalDateTime returnDate;
    private String returnReference;
    private BigDecimal estimatedLoss;
    private String notes;
    private Integer daysInQuarantine;
    private Boolean canApprove;
    private Boolean canDispose;
    private Boolean canReturn;
}