package com.medicorex.dto;

import com.medicorex.entity.Notification.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private Long id;
    private Long userId;
    private String username;
    private String type;
    private NotificationCategory category;
    private String title;
    private String message;
    private NotificationPriority priority;
    private NotificationStatus status;
    private String actionUrl;
    private Map<String, Object> actionData;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime expiresAt;
}