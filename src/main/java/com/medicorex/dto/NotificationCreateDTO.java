package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Temporary stub for NotificationCreateDTO
 * Will be fully implemented in Phase 2: Notification System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCreateDTO {
    private Long userId;
    private String title;
    private String message;
    private String type;
    private String priority;
    private Map<String, Object> data;
}