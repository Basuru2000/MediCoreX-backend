package com.medicorex.service;

import com.medicorex.dto.NotificationDTO;
import com.medicorex.dto.NotificationCreateDTO;
import com.medicorex.dto.NotificationSummaryDTO;
import com.medicorex.dto.PageResponseDTO;
import com.medicorex.entity.Notification;
import com.medicorex.entity.Notification.*;
import com.medicorex.entity.NotificationTemplate;
import com.medicorex.entity.User;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.NotificationRepository;
import com.medicorex.repository.NotificationTemplateRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final UserRepository userRepository;

    /**
     * Create notification from template
     */
    public NotificationDTO createNotificationFromTemplate(
            Long userId,
            String templateCode,
            Map<String, String> params,
            Map<String, Object> actionData) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        NotificationTemplate template = templateRepository.findByCodeAndActive(templateCode, true)
                .orElseThrow(() -> new ResourceNotFoundException("Template", "code", templateCode));

        Notification notification = Notification.builder()
                .user(user)
                .type(templateCode)
                .category(NotificationCategory.valueOf(template.getCategory()))
                .title(template.processTitle(params))
                .message(template.processMessage(params))
                .priority(template.getPriority())
                .status(NotificationStatus.UNREAD)
                .actionUrl(generateActionUrl(templateCode, actionData))
                .actionData(actionData)
                .createdAt(LocalDateTime.now())
                .build();

        // Set expiry for low priority notifications (30 days)
        if (notification.getPriority() == NotificationPriority.LOW) {
            notification.setExpiresAt(LocalDateTime.now().plusDays(30));
        }

        Notification saved = notificationRepository.save(notification);

        // Update user's unread count
        updateUserUnreadCount(userId);

        log.info("Created notification for user {} from template {}", userId, templateCode);

        return convertToDTO(saved);
    }

    /**
     * Create custom notification
     */
    public NotificationDTO createCustomNotification(NotificationCreateDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dto.getUserId()));

        Notification notification = Notification.builder()
                .user(user)
                .type(dto.getType())
                .category(dto.getCategory())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .priority(dto.getPriority() != null ? dto.getPriority() : NotificationPriority.MEDIUM)
                .status(NotificationStatus.UNREAD)
                .actionUrl(dto.getActionUrl())
                .actionData(dto.getActionData())
                .createdAt(LocalDateTime.now())
                .expiresAt(dto.getExpiresAt())
                .build();

        Notification saved = notificationRepository.save(notification);
        updateUserUnreadCount(dto.getUserId());

        return convertToDTO(saved);
    }

    /**
     * Send notification to multiple users by role
     */
    @Async
    public void notifyUsersByRole(List<String> roles, String templateCode,
                                  Map<String, String> params, Map<String, Object> actionData) {
        List<User> users = userRepository.findByRoleIn(roles);

        for (User user : users) {
            try {
                createNotificationFromTemplate(user.getId(), templateCode, params, actionData);
            } catch (Exception e) {
                log.error("Failed to create notification for user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Sent {} notifications to users with roles {}", users.size(), roles);
    }

    /**
     * Get notifications for user
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<NotificationDTO> getUserNotifications(
            Long userId,
            NotificationStatus status,
            NotificationCategory category,
            NotificationPriority priority,
            Pageable pageable) {
        // Use single dynamic query
        Page<Notification> notificationPage = notificationRepository.findByUserIdWithFilters(
                userId, status, category, priority, pageable);
        // Convert entities to DTOs
        List<NotificationDTO> dtos = notificationPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        // Create PageResponseDTO without builder
        PageResponseDTO<NotificationDTO> response = new PageResponseDTO<>();
        response.setContent(dtos);
        response.setPage(notificationPage.getNumber());
        response.setTotalPages(notificationPage.getTotalPages());
        response.setTotalElements(notificationPage.getTotalElements());
        response.setLast(notificationPage.isLast());

        return response;

        // OR if PageResponseDTO has all-args constructor:
        /*
        return new PageResponseDTO<>(
            dtos,
            notificationPage.getNumber(),
            notificationPage.getTotalPages(),
            notificationPage.getTotalElements(),
            notificationPage.isLast()
        );
        */
    }

    /**
     * Mark notification as read
     */
    public NotificationDTO markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (notification.getStatus() != NotificationStatus.READ) {
            notification.markAsRead();
            notification = notificationRepository.save(notification);
            updateUserUnreadCount(notification.getUser().getId());
        }

        return convertToDTO(notification);
    }

    /**
     * Mark all notifications as read for user
     */
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadForUser(userId, LocalDateTime.now());
        updateUserUnreadCount(userId);
        log.info("Marked all notifications as read for user {}", userId);
    }

    /**
     * Archive notification
     */
    public void archiveNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        notification.archive();
        notificationRepository.save(notification);

        if (notification.getStatus() == NotificationStatus.UNREAD) {
            updateUserUnreadCount(notification.getUser().getId());
        }
    }

    /**
     * Delete notification
     */
    public void deleteNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        Long userId = notification.getUser().getId();
        boolean wasUnread = notification.getStatus() == NotificationStatus.UNREAD;

        notificationRepository.deleteById(notificationId);

        if (wasUnread) {
            updateUserUnreadCount(userId);
        }
    }

    /**
     * Get notification summary for user
     * FIXED: Handle null values from SQL query
     */
    @Transactional(readOnly = true)
    public NotificationSummaryDTO getNotificationSummary(Long userId) {
        Object[] summary = notificationRepository.getNotificationSummary(userId);

        if (summary != null && summary.length > 0 && summary[0] != null) {
            Object[] data = (Object[]) summary[0];

            // FIX: Handle null values with proper null checks
            Long totalCount = data[0] != null ? ((Number) data[0]).longValue() : 0L;
            Long unreadCount = data[1] != null ? ((Number) data[1]).longValue() : 0L;
            Long criticalCount = data[2] != null ? ((Number) data[2]).longValue() : 0L;

            return NotificationSummaryDTO.builder()
                    .totalCount(totalCount)
                    .unreadCount(unreadCount)
                    .criticalCount(criticalCount)
                    .highPriorityCount(0L)  // You can calculate this if needed
                    .todayCount(0L)  // You can calculate this if needed
                    .build();
        }

        // Return default values if no data
        return NotificationSummaryDTO.builder()
                .totalCount(0L)
                .unreadCount(0L)
                .criticalCount(0L)
                .highPriorityCount(0L)
                .todayCount(0L)
                .build();
    }

    /**
     * Get unread count for user
     */
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    /**
     * Get recent critical notifications
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getRecentCriticalNotifications(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return notificationRepository.findRecentCriticalNotifications(userId, since)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Clean up old notifications (scheduled task)
     */
    @Transactional
    public void cleanupOldNotifications() {
        // Delete expired notifications
        notificationRepository.deleteExpiredNotifications(LocalDateTime.now());

        // Delete archived notifications older than 90 days
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        notificationRepository.deleteOldArchivedNotifications(cutoffDate);

        log.info("Cleaned up old and expired notifications");
    }

    /**
     * Check and create stock alert notifications
     */
    public void createStockAlertNotification(Long productId, String productName,
                                             Integer currentQuantity, Integer minStock) {
        Map<String, String> params = new HashMap<>();
        params.put("productName", productName);
        params.put("quantity", String.valueOf(currentQuantity));
        params.put("minStock", String.valueOf(minStock));

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("productId", productId);
        actionData.put("type", "stock_alert");

        String templateCode = currentQuantity == 0 ? "STOCK_OUT" : "STOCK_LOW";

        // Notify relevant roles
        notifyUsersByRole(
                Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF", "PROCUREMENT_OFFICER"),
                templateCode, params, actionData
        );
    }

    /**
     * Create expiry alert notification
     */
    public void createExpiryAlertNotification(Long batchId, String productName,
                                              String batchNumber, Integer daysUntilExpiry) {
        Map<String, String> params = new HashMap<>();
        params.put("productName", productName);
        params.put("batchNumber", batchNumber);
        params.put("days", String.valueOf(daysUntilExpiry));

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("batchId", batchId);
        actionData.put("type", "expiry_alert");

        String templateCode;
        if (daysUntilExpiry <= 0) {
            templateCode = "EXPIRED";
        } else if (daysUntilExpiry <= 7) {
            templateCode = "EXPIRY_7_DAYS";
        } else if (daysUntilExpiry <= 30) {
            templateCode = "EXPIRY_30_DAYS";
        } else if (daysUntilExpiry <= 60) {
            templateCode = "EXPIRY_60_DAYS";
        } else {
            templateCode = "EXPIRY_90_DAYS";
        }

        notifyUsersByRole(
                Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                templateCode, params, actionData
        );
    }

    /**
     * Clean up archived notifications older than specified days
     */
    @Transactional
    public int cleanupArchivedNotifications(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        return notificationRepository.deleteByStatusAndCreatedAtBefore(
                NotificationStatus.ARCHIVED, cutoffDate);
    }

    /**
     * Clean up expired notifications
     */
    @Transactional
    public int cleanupExpiredNotifications() {
        return notificationRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    /**
     * Clean up old read notifications
     */
    @Transactional
    public int cleanupOldReadNotifications(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        return notificationRepository.deleteByStatusAndReadAtBefore(
                NotificationStatus.READ, cutoffDate);
    }

    /**
     * Send daily summary to managers
     */
    @Transactional
    public int sendDailySummaryToManagers() {
        List<User> managers = userRepository.findByRole("HOSPITAL_MANAGER");
        int summariesSent = 0;

        for (User manager : managers) {
            try {
                // Get critical unread count
                Long criticalCount = notificationRepository.countByUserIdAndStatusAndPriority(
                        manager.getId(), NotificationStatus.UNREAD, NotificationPriority.CRITICAL);

                Long highCount = notificationRepository.countByUserIdAndStatusAndPriority(
                        manager.getId(), NotificationStatus.UNREAD, NotificationPriority.HIGH);

                if (criticalCount > 0 || highCount > 0) {
                    Map<String, String> params = new HashMap<>();
                    params.put("criticalCount", String.valueOf(criticalCount));
                    params.put("highCount", String.valueOf(highCount));
                    params.put("date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    createNotificationFromTemplate(
                            manager.getId(),
                            "DAILY_SUMMARY",
                            params,
                            Map.of("action", "VIEW_ALL")
                    );
                    summariesSent++;
                }
            } catch (Exception e) {
                log.error("Failed to send summary to manager {}: {}", manager.getId(), e.getMessage());
            }
        }

        return summariesSent;
    }

    /**
     * Escalate critical notifications
     */
    @Transactional
    public int escalateCriticalNotifications(int hoursThreshold) {
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(hoursThreshold);

        List<Notification> criticalUnread = notificationRepository
                .findByStatusAndPriorityAndCreatedAtBefore(
                        NotificationStatus.UNREAD,
                        NotificationPriority.CRITICAL,
                        thresholdTime
                );

        int escalated = 0;
        for (Notification notification : criticalUnread) {
            try {
                // Create escalation notification for all managers
                List<User> managers = userRepository.findByRole("HOSPITAL_MANAGER");
                for (User manager : managers) {
                    if (!manager.getId().equals(notification.getUser().getId())) {
                        Map<String, String> params = new HashMap<>();
                        params.put("originalTitle", notification.getTitle());
                        params.put("username", notification.getUser().getUsername());
                        params.put("hours", String.valueOf(hoursThreshold));

                        createNotificationFromTemplate(
                                manager.getId(),
                                "CRITICAL_ESCALATION",
                                params,
                                Map.of("originalId", notification.getId())
                        );
                        escalated++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to escalate notification {}: {}", notification.getId(), e.getMessage());
            }
        }

        return escalated;
    }

    /**
     * Group similar notifications
     */
    @Transactional
    public int groupSimilarNotifications() {
        List<User> users = userRepository.findAll();
        int totalGrouped = 0;

        for (User user : users) {
            // Find unread notifications for this user
            List<Notification> unreadNotifications = notificationRepository
                    .findByUserIdAndStatus(user.getId(), NotificationStatus.UNREAD);

            // Group by type and category
            Map<String, List<Notification>> grouped = unreadNotifications.stream()
                    .filter(n -> n.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                    .collect(Collectors.groupingBy(n -> n.getType() + "_" + n.getCategory()));

            for (Map.Entry<String, List<Notification>> entry : grouped.entrySet()) {
                if (entry.getValue().size() >= 3) { // Group if 3 or more similar
                    try {
                        // Create grouped notification
                        Notification groupedNotification = Notification.builder()
                                .user(user)
                                .type("GROUPED_" + entry.getValue().get(0).getType())
                                .category(entry.getValue().get(0).getCategory())
                                .title(entry.getValue().size() + " similar notifications")
                                .message("You have " + entry.getValue().size() + " similar " +
                                        entry.getValue().get(0).getCategory() + " notifications")
                                .priority(entry.getValue().get(0).getPriority())
                                .status(NotificationStatus.UNREAD)
                                .actionData(Map.of("groupedIds",
                                        entry.getValue().stream()
                                                .map(Notification::getId)
                                                .collect(Collectors.toList())))
                                .build();

                        notificationRepository.save(groupedNotification);

                        // Archive original notifications
                        entry.getValue().forEach(n -> {
                            n.setStatus(NotificationStatus.ARCHIVED);
                            notificationRepository.save(n);
                        });

                        totalGrouped += entry.getValue().size();
                    } catch (Exception e) {
                        log.error("Failed to group notifications: {}", e.getMessage());
                    }
                }
            }
        }

        return totalGrouped;
    }

    // Helper methods

    private void updateUserUnreadCount(Long userId) {
        Long unreadCount = notificationRepository.countByUserIdAndStatus(
                userId, NotificationStatus.UNREAD);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setUnreadNotifications(unreadCount.intValue());
            user.setLastNotificationCheck(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    private String generateActionUrl(String templateCode, Map<String, Object> actionData) {
        if (actionData == null || actionData.isEmpty()) {
            return null;
        }

        // Generate appropriate URL based on notification type
        if (templateCode.startsWith("QUARANTINE")) {
            Long recordId = (Long) actionData.get("recordId");
            return recordId != null ? "/quarantine/" + recordId : "/quarantine";
        } else if (templateCode.startsWith("STOCK")) {
            Long productId = (Long) actionData.get("productId");
            return productId != null ? "/products/" + productId : "/products";
        } else if (templateCode.startsWith("EXPIRY") || templateCode.startsWith("BATCH")) {
            Long batchId = (Long) actionData.get("batchId");
            return batchId != null ? "/batch-tracking/" + batchId : "/batch-tracking";
        } else if (templateCode.startsWith("REPORT")) {
            String reportId = (String) actionData.get("reportId");
            return reportId != null ? "/reports/" + reportId : "/reports";
        }

        return null;
    }

    private NotificationDTO convertToDTO(Notification notification) {
        return NotificationDTO.builder()
                .id(notification.getId())
                .userId(notification.getUser().getId())
                .username(notification.getUser().getUsername())
                .type(notification.getType())
                .category(notification.getCategory())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .priority(notification.getPriority())
                .status(notification.getStatus())
                .actionUrl(notification.getActionUrl())
                .actionData(notification.getActionData())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .expiresAt(notification.getExpiresAt())
                .build();
    }
}