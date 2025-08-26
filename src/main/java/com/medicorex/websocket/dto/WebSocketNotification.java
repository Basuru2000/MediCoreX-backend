package com.medicorex.websocket.dto;

import com.medicorex.dto.NotificationDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketNotification {

    public enum EventType {
        NEW_NOTIFICATION,
        NOTIFICATION_READ,
        NOTIFICATION_DELETED,
        NOTIFICATION_ARCHIVED,
        BATCH_CREATED,
        BATCH_EXPIRED,
        STOCK_LOW,
        STOCK_CRITICAL,
        QUARANTINE_ALERT,
        EXPIRY_ALERT,
        SYSTEM_ALERT
    }

    private Long notificationId;
    private EventType eventType;
    private String username;
    private NotificationDTO notification;
    private Integer unreadCount;
    private LocalDateTime timestamp;
    private boolean requiresAction;
    private String actionLabel;
    private String actionUrl;

    // Factory methods for common events
    public static WebSocketNotification newNotification(NotificationDTO notification, String username, Integer unreadCount) {
        return WebSocketNotification.builder()
                .notificationId(notification.getId())
                .eventType(EventType.NEW_NOTIFICATION)
                .username(username)
                .notification(notification)
                .unreadCount(unreadCount)
                .timestamp(LocalDateTime.now())
                .requiresAction(notification.getPriority().equals("CRITICAL"))
                .build();
    }

    public static WebSocketNotification notificationRead(Long notificationId, String username, Integer unreadCount) {
        return WebSocketNotification.builder()
                .notificationId(notificationId)
                .eventType(EventType.NOTIFICATION_READ)
                .username(username)
                .unreadCount(unreadCount)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static WebSocketNotification stockAlert(String username, String message, String actionUrl) {
        return WebSocketNotification.builder()
                .eventType(EventType.STOCK_LOW)
                .username(username)
                .timestamp(LocalDateTime.now())
                .requiresAction(true)
                .actionLabel("View Stock")
                .actionUrl(actionUrl)
                .build();
    }
}