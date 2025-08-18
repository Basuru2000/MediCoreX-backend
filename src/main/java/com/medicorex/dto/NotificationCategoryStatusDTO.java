package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationCategoryStatusDTO {
    private String category;
    private Boolean enabled;
    private String frequency;
    private String description;
    private Integer unreadCount;
}