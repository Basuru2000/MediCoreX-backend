package com.medicorex.service.notification;

import com.medicorex.dto.NotificationCreateDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Temporary stub for NotificationService
 * Will be fully implemented in Phase 2: Notification System
 */
@Slf4j
@Service
public class NotificationService {

    /**
     * Stub method - logs notification instead of sending
     * Will be replaced with actual implementation in Phase 2
     */
    public void createNotification(NotificationCreateDTO notification) {
        // Temporary implementation - just log the notification
        log.info("NOTIFICATION [{}]: {} - {} (User: {})",
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getUserId()
        );

        // In Phase 2, this will:
        // 1. Save to database
        // 2. Send via WebSocket
        // 3. Trigger email/SMS if configured
    }

    /**
     * Stub method for sending bulk notifications
     */
    public void sendBulkNotifications(String title, String message, String type) {
        log.info("BULK NOTIFICATION [{}]: {} - {}", type, title, message);
    }
}