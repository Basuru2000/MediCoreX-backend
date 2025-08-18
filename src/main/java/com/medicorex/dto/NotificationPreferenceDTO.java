package com.medicorex.dto;

import com.medicorex.entity.Notification;
import com.medicorex.entity.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceDTO {

    private Long id;
    private Long userId;
    private String username;

    // Global settings
    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;

    // Category preferences
    private Map<String, Boolean> categoryPreferences;

    // Priority threshold
    private String priorityThreshold;

    // Quiet hours
    private QuietHoursDTO quietHours;

    // Frequency settings
    private Map<String, String> frequencySettings;

    // Digest settings
    private Boolean digestEnabled;
    private String digestTime;
    private LocalDateTime lastDigestSent;

    // Escalation
    private Boolean escalationEnabled;
    private String escalationContact;

    // Sound/Visual
    private Boolean soundEnabled;
    private Boolean desktopNotifications;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuietHoursDTO {
        private Boolean enabled;
        private String startTime;
        private String endTime;
        private String timezone;
    }
}