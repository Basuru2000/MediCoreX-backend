package com.medicorex.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceUpdateDTO {

    // Global settings (all optional for partial updates)
    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;

    // Category preferences
    private Map<String, Boolean> categoryPreferences;

    // Priority threshold
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
            message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priorityThreshold;

    // Quiet hours
    private QuietHoursUpdateDTO quietHours;

    // Frequency settings
    private Map<String, String> frequencySettings;

    // Digest settings
    private Boolean digestEnabled;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
            message = "Time must be in HH:mm format")
    private String digestTime;

    // Escalation
    private Boolean escalationEnabled;
    private String escalationContact;

    // Sound/Visual
    private Boolean soundEnabled;
    private Boolean desktopNotifications;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuietHoursUpdateDTO {
        private Boolean enabled;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
                message = "Time must be in HH:mm format")
        private String startTime;

        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
                message = "Time must be in HH:mm format")
        private String endTime;

        private String timezone;
    }
}