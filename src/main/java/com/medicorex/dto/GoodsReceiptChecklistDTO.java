package com.medicorex.dto;

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
public class GoodsReceiptChecklistDTO {
    private Long id;
    private Long receiptId;
    private String receiptNumber;
    private Long templateId;
    private String templateName;
    private Long completedById;
    private String completedByName;
    private LocalDateTime completedAt;
    private String overallResult;
    private String inspectorNotes;
    private List<ChecklistAnswerDTO> answers;
    private Integer totalChecks;
    private Integer passedChecks;
    private Integer failedChecks;
}