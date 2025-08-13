package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "notification_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "title_template", nullable = false, length = 500)
    private String titleTemplate;

    @Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
    private String messageTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default  // FIX: Added @Builder.Default
    private Notification.NotificationPriority priority = Notification.NotificationPriority.MEDIUM;

    @Column(nullable = false)
    @Builder.Default  // FIX: Added @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default  // FIX: Added @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Process template with parameters
     */
    public String processTitle(Map<String, String> params) {
        return processTemplate(titleTemplate, params);
    }

    public String processMessage(Map<String, String> params) {
        return processTemplate(messageTemplate, params);
    }

    private String processTemplate(String template, Map<String, String> params) {
        String result = template;
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                result = result.replace(placeholder, entry.getValue());
            }
        }
        return result;
    }
}