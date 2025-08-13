package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationCategory category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default  // FIX: Added @Builder.Default
    private NotificationPriority priority = NotificationPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default  // FIX: Added @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "action_data", columnDefinition = "JSON")
    @Convert(converter = HashMapConverter.class)
    private Map<String, Object> actionData;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default  // FIX: Added @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public enum NotificationCategory {
        QUARANTINE,
        STOCK,
        EXPIRY,
        BATCH,
        USER,
        SYSTEM,
        APPROVAL,
        REPORT,
        PROCUREMENT
    }

    public enum NotificationPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum NotificationStatus {
        UNREAD,
        READ,
        ARCHIVED
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = NotificationStatus.UNREAD;
        }
        if (priority == null) {
            priority = NotificationPriority.MEDIUM;
        }
    }

    public void markAsRead() {
        this.status = NotificationStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    public void archive() {
        this.status = NotificationStatus.ARCHIVED;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isCritical() {
        return priority == NotificationPriority.CRITICAL;
    }

    public boolean isHighPriority() {
        return priority == NotificationPriority.HIGH || priority == NotificationPriority.CRITICAL;
    }
}