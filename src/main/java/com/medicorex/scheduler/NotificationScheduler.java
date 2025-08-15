package com.medicorex.scheduler;

import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;

    @Value("${notification.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${notification.cleanup.archive.days:90}")
    private int archiveDays;

    @Value("${notification.cleanup.expired.days:30}")
    private int expiredDays;

    /**
     * Clean up old notifications daily at 3 AM
     * This removes archived notifications older than configured days
     * and expired notifications
     */
    @Scheduled(cron = "${notification.cleanup.cron:0 0 3 * * ?}")
    public void cleanupOldNotifications() {
        if (!cleanupEnabled) {
            log.debug("Notification cleanup is disabled");
            return;
        }

        log.info("Starting scheduled notification cleanup task");
        try {
            // Clean up archived notifications older than X days
            int archivedCleaned = notificationService.cleanupArchivedNotifications(archiveDays);
            log.info("Cleaned up {} archived notifications older than {} days", archivedCleaned, archiveDays);

            // Clean up expired notifications
            int expiredCleaned = notificationService.cleanupExpiredNotifications();
            log.info("Cleaned up {} expired notifications", expiredCleaned);

            // Clean up read notifications older than X days
            int readCleaned = notificationService.cleanupOldReadNotifications(expiredDays);
            log.info("Cleaned up {} read notifications older than {} days", readCleaned, expiredDays);

            log.info("Notification cleanup completed successfully. Total cleaned: {}",
                    archivedCleaned + expiredCleaned + readCleaned);
        } catch (Exception e) {
            log.error("Error during notification cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Send daily summary notifications at 8 AM
     * Summarizes critical unread notifications for managers
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDailySummary() {
        log.info("Starting daily notification summary task");
        try {
            int summariesSent = notificationService.sendDailySummaryToManagers();
            log.info("Daily summaries sent to {} managers", summariesSent);
        } catch (Exception e) {
            log.error("Error during daily summary generation: {}", e.getMessage(), e);
        }
    }

    /**
     * Check for critical unresolved notifications every hour
     * Escalates critical notifications that haven't been read for > 4 hours
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void escalateCriticalNotifications() {
        log.debug("Checking for critical unresolved notifications");
        try {
            int escalated = notificationService.escalateCriticalNotifications(4); // 4 hours threshold
            if (escalated > 0) {
                log.warn("Escalated {} critical notifications", escalated);
            }
        } catch (Exception e) {
            log.error("Error during critical notification check: {}", e.getMessage(), e);
        }
    }

    /**
     * Group similar notifications every 15 minutes
     * Combines multiple similar notifications into grouped ones
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes in milliseconds
    public void groupSimilarNotifications() {
        log.trace("Checking for similar notifications to group");
        try {
            int grouped = notificationService.groupSimilarNotifications();
            if (grouped > 0) {
                log.debug("Grouped {} similar notifications", grouped);
            }
        } catch (Exception e) {
            log.error("Error grouping notifications: {}", e.getMessage(), e);
        }
    }
}