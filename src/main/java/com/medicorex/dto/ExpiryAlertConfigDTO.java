package com.medicorex.dto;

import com.medicorex.entity.ExpiryAlertConfig.AlertSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpiryAlertConfigDTO {
    private Long id;
    private String tierName;
    private Integer daysBeforeExpiry;
    private AlertSeverity severity;
    private String description;
    private Boolean active;
    private List<String> notifyRoles;
    private String colorCode;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Additional computed fields
    private Long activeAlertCount;
    private Long affectedProductCount;
}