package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender = Gender.NOT_SPECIFIED;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    // NEW FIELDS FOR NOTIFICATION SUPPORT:

    @Column(name = "unread_notifications")
    private Integer unreadNotifications = 0;

    @Column(name = "last_notification_check")
    private LocalDateTime lastNotificationCheck;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // GETTER/SETTER METHODS FOR NOTIFICATION FIELDS:

    public Integer getUnreadNotifications() {
        return unreadNotifications != null ? unreadNotifications : 0;
    }

    public void setUnreadNotifications(Integer unreadNotifications) {
        this.unreadNotifications = unreadNotifications;
    }

    public LocalDateTime getLastNotificationCheck() {
        return lastNotificationCheck;
    }

    public void setLastNotificationCheck(LocalDateTime lastNotificationCheck) {
        this.lastNotificationCheck = lastNotificationCheck;
    }

    public enum UserRole {
        HOSPITAL_MANAGER,
        PHARMACY_STAFF,
        PROCUREMENT_OFFICER
    }

    public enum Gender {
        MALE,
        FEMALE,
        NOT_SPECIFIED
    }
}