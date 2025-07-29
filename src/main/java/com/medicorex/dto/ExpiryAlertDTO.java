package com.medicorex.dto;

import com.medicorex.entity.ExpiryAlert.AlertStatus;
import com.medicorex.entity.ExpiryAlertConfig.AlertSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpiryAlertDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productCode;
    private String batchNumber;
    private Long configId;
    private String tierName;
    private AlertSeverity severity;
    private LocalDate alertDate;
    private LocalDate expiryDate;
    private Integer quantityAffected;
    private AlertStatus status;
    private String acknowledgedByName;
    private LocalDateTime acknowledgedAt;
    private String notes;
    private LocalDateTime createdAt;

    // Computed fields
    private Integer daysUntilExpiry;
    private String colorCode;
}