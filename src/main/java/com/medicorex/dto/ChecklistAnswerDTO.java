package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistAnswerDTO {
    private Long id;
    private Long checklistId;
    private Long checkItemId;
    private String checkDescription;
    private String answer;
    private Boolean isCompliant;
    private String remarks;
    private Boolean isMandatory;
}