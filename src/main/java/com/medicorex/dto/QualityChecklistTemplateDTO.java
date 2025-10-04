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
public class QualityChecklistTemplateDTO {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Boolean isActive;
    private Boolean isDefault;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QualityCheckItemDTO> items;
    private Integer itemCount;
}