package com.medicorex.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistSubmissionDTO {

    @NotNull(message = "Receipt ID is required")
    private Long receiptId;

    @NotNull(message = "Template ID is required")
    private Long templateId;

    private String inspectorNotes;

    @NotEmpty(message = "At least one answer is required")
    private List<AnswerSubmission> answers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerSubmission {
        @NotNull(message = "Check item ID is required")
        private Long checkItemId;

        @NotNull(message = "Answer is required")
        private String answer;

        private String remarks;
    }
}