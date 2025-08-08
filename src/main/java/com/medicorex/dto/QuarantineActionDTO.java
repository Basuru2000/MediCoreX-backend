package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineActionDTO {

    @NotNull(message = "Quarantine record ID is required")
    private Long quarantineRecordId;

    @NotBlank(message = "Action is required")
    private String action; // REVIEW, APPROVE_DISPOSAL, APPROVE_RETURN, DISPOSE, RETURN

    private String comments;
    private String disposalMethod;
    private String disposalCertificate;
    private String returnReference;

    // performedBy is set by the controller from Principal - no validation needed
    private String performedBy;
}