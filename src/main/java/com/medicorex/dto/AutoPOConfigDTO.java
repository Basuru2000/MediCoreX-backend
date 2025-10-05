package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPOConfigDTO {
    private Long id;
    private Boolean enabled;
    private String scheduleCron;
    private BigDecimal reorderMultiplier;
    private Integer daysUntilDelivery;
    private BigDecimal minPoValue;
    private Boolean onlyPreferredSuppliers;
    private Boolean autoApprove;
    private Boolean notificationEnabled;
    private String notifyRoles;
    private LocalDateTime lastRunAt;
    private String lastRunStatus;
    private String lastRunDetails;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}