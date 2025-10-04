package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckItemDTO {
    private Long id;
    private Long templateId;
    private Integer itemOrder;
    private String checkDescription;
    private String checkType;
    private Boolean isMandatory;
    private String expectedValue;
    private String notes;
}