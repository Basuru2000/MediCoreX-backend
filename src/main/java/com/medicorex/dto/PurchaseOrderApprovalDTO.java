package com.medicorex.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderApprovalDTO {

    @Size(max = 1000, message = "Comments cannot exceed 1000 characters")
    private String comments;
}