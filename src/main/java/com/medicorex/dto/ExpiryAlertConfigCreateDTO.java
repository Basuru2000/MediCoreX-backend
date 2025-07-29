package com.medicorex.dto;

import com.medicorex.entity.ExpiryAlertConfig.AlertSeverity;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpiryAlertConfigCreateDTO {

    @NotBlank(message = "Tier name is required")
    @Size(max = 100, message = "Tier name must not exceed 100 characters")
    private String tierName;

    @NotNull(message = "Days before expiry is required")
    @Min(value = 1, message = "Days before expiry must be at least 1")
    @Max(value = 365, message = "Days before expiry must not exceed 365")
    private Integer daysBeforeExpiry;

    @NotNull(message = "Severity is required")
    private AlertSeverity severity;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private Boolean active = true;

    @NotEmpty(message = "At least one role must be selected")
    private List<String> notifyRoles;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color code must be a valid hex color")
    private String colorCode;

    private Integer sortOrder;
}