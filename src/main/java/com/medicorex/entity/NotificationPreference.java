package com.medicorex.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Entity
@Table(name = "notification_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    // Global notification settings
    @Column(name = "in_app_enabled")
    @Builder.Default
    private Boolean inAppEnabled = true;

    @Column(name = "email_enabled")
    @Builder.Default
    private Boolean emailEnabled = false;

    @Column(name = "sms_enabled")
    @Builder.Default
    private Boolean smsEnabled = false;

    // Category-specific preferences
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_preferences", columnDefinition = "json")
    private Map<String, Boolean> categoryPreferences;

    // Priority threshold
    @Column(name = "priority_threshold")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Notification.NotificationPriority priorityThreshold = Notification.NotificationPriority.LOW;

    // Quiet hours configuration
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiet_hours", columnDefinition = "json")
    private QuietHours quietHours;

    // Frequency settings
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frequency_settings", columnDefinition = "json")
    private Map<String, FrequencyType> frequencySettings;

    // Daily digest settings
    @Column(name = "digest_enabled")
    @Builder.Default
    private Boolean digestEnabled = false;

    @Column(name = "digest_time")
    @Builder.Default
    private LocalTime digestTime = LocalTime.of(9, 0);

    @Column(name = "last_digest_sent")
    private LocalDateTime lastDigestSent;

    // Escalation preferences
    @Column(name = "escalation_enabled")
    @Builder.Default
    private Boolean escalationEnabled = true;

    @Column(name = "escalation_contact")
    private String escalationContact;

    // Sound/Visual preferences
    @Column(name = "sound_enabled")
    @Builder.Default
    private Boolean soundEnabled = true;

    @Column(name = "desktop_notifications")
    @Builder.Default
    private Boolean desktopNotifications = true;

    // Metadata
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Inner class for Quiet Hours
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuietHours {
        @Builder.Default
        private Boolean enabled = false;
        @Builder.Default
        private String startTime = "22:00"; // HH:mm format
        @Builder.Default
        private String endTime = "07:00";   // HH:mm format
        @Builder.Default
        private String timezone = "UTC";
    }

    // Enum for notification frequency
    public enum FrequencyType {
        IMMEDIATE,
        HOURLY_DIGEST,
        DAILY_DIGEST,
        WEEKLY_DIGEST,
        DISABLED
    }

    /**
     * Check if a specific category is enabled
     */
    public boolean isCategoryEnabled(String category) {
        if (categoryPreferences == null || !inAppEnabled) {
            return false;
        }
        return categoryPreferences.getOrDefault(category, true);
    }

    /**
     * Check if notification priority meets threshold
     */
    public boolean meetsPriorityThreshold(Notification.NotificationPriority priority) {
        if (priorityThreshold == null) {
            return true;
        }
        return priority.ordinal() >= priorityThreshold.ordinal();
    }

    /**
     * Check if current time is within quiet hours
     */
    public boolean isInQuietHours(LocalTime currentTime) {
        if (quietHours == null || !quietHours.enabled) {
            return false;
        }

        try {
            LocalTime start = LocalTime.parse(quietHours.startTime);
            LocalTime end = LocalTime.parse(quietHours.endTime);

            // Handle overnight quiet hours (e.g., 22:00 to 07:00)
            if (start.isAfter(end)) {
                return currentTime.isAfter(start) || currentTime.isBefore(end);
            } else {
                return currentTime.isAfter(start) && currentTime.isBefore(end);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determine if notification should be sent based on all preferences
     */
    public boolean shouldSendNotification(String category,
                                          Notification.NotificationPriority priority,
                                          LocalTime currentTime) {
        // Check if in-app notifications are enabled
        if (!inAppEnabled) {
            return false;
        }

        // Check category preference
        if (!isCategoryEnabled(category)) {
            return false;
        }

        // Check priority threshold
        if (!meetsPriorityThreshold(priority)) {
            return false;
        }

        // Skip quiet hours check for CRITICAL priority
        if (priority != Notification.NotificationPriority.CRITICAL) {
            if (isInQuietHours(currentTime)) {
                return false;
            }
        }

        return true;
    }
}