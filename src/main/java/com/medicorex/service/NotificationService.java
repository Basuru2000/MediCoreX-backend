package com.medicorex.service;

import com.medicorex.dto.NotificationDTO;
import com.medicorex.dto.NotificationCreateDTO;
import com.medicorex.dto.NotificationSummaryDTO;
import com.medicorex.dto.PageResponseDTO;
import com.medicorex.entity.Notification;
import com.medicorex.entity.Notification.*;
import com.medicorex.entity.NotificationTemplate;
import com.medicorex.entity.User;
import com.medicorex.entity.User.UserRole;
import com.medicorex.entity.Product;
import com.medicorex.entity.ProductBatch;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
     * Send notification to multiple users by role - ASYNC version
     * This method accepts String roles and converts them internally
     */
    @Async
    public void notifyUsersByRole(List<String> roleStrings, String templateCode,
                                  Map<String, String> params, Map<String, Object> actionData) {
        try {
            log.info("=== Starting ASYNC notification send to roles: {} with template: {}", roleStrings, templateCode);

            // Convert string roles to enum values
            List<User.UserRole> roleEnums = new ArrayList<>();
            for (String roleStr : roleStrings) {
                try {
                    User.UserRole role = User.UserRole.valueOf(roleStr.toUpperCase().replace(" ", "_"));
                    roleEnums.add(role);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid role string: {}", roleStr);
                }
            }

            if (!roleEnums.isEmpty()) {
                List<User> users = userRepository.findByRoleIn(roleEnums);
                log.info("Found {} users with roles {}", users.size(), roleEnums);

                int successCount = 0;
                int failCount = 0;

                for (User user : users) {
                    try {
                        NotificationDTO notification = createNotificationFromTemplate(
                                user.getId(),
                                templateCode,
                                params,
                                actionData
                        );
                        log.info("Created notification {} for user: {}", notification.getId(), user.getUsername());
                        successCount++;
                    } catch (Exception e) {
                        failCount++;
                        log.error("Failed to create notification for user {} ({}): {}",
                                user.getId(), user.getUsername(), e.getMessage());
                    }
                }

                log.info("=== Notification send completed. Success: {}, Failed: {} for template: {}",
                        successCount, failCount, templateCode);
            } else {
                log.warn("No valid roles found from: {}", roleStrings);
            }

        } catch (Exception e) {
            log.error("=== CRITICAL ERROR in notifyUsersByRole: {}", e.getMessage(), e);
        }
    }

    /**
     * SYNCHRONOUS version for testing - accepts String roles
     */
    public void notifyUsersByRoleSync(List<String> roleStrings, String templateCode,
                                      Map<String, String> params, Map<String, Object> actionData) {
        log.info("=== SYNC notification send to roles: {} with template: {}", roleStrings, templateCode);

        try {
            // Convert string roles to enum values
            List<User.UserRole> roleEnums = new ArrayList<>();
            for (String roleStr : roleStrings) {
                try {
                    User.UserRole role = User.UserRole.valueOf(roleStr.toUpperCase().replace(" ", "_"));
                    roleEnums.add(role);
                    log.debug("Converted role string '{}' to enum {}", roleStr, role);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid role string: {}", roleStr);
                }
            }

            if (roleEnums.isEmpty()) {
                log.error("No valid roles found from: {}", roleStrings);
                return;
            }

            // Find users with these roles
            List<User> users = userRepository.findByRoleIn(roleEnums);
            log.info("Found {} users with roles {}", users.size(), roleEnums);

            if (users.isEmpty()) {
                log.warn("No users found with roles: {}", roleEnums);

                // Debug: Show all users and their roles
                List<User> allUsers = userRepository.findAll();
                log.debug("All users in system:");
                for (User u : allUsers) {
                    log.debug("  - {} ({}): role={}", u.getId(), u.getUsername(), u.getRole());
                }
                return;
            }

            // Create notifications for each user
            for (User user : users) {
                try {
                    NotificationDTO notification = createNotificationFromTemplate(
                            user.getId(),
                            templateCode,
                            params,
                            actionData
                    );
                    log.info("✓ Created notification {} for user {} ({})",
                            notification.getId(), user.getUsername(), user.getRole());

                } catch (Exception e) {
                    log.error("✗ Failed to create notification for user {} ({}): {}",
                            user.getUsername(), user.getRole(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("SYNC notification send failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send notifications", e);
        }
    }

    /**
     * Get notifications for user with filters
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<NotificationDTO> getUserNotifications(
            Long userId,
            NotificationStatus status,
            NotificationCategory category,
            NotificationPriority priority,
            Pageable pageable) {

        Page<Notification> notificationPage = notificationRepository.findByUserIdWithFilters(
                userId, status, category, priority, pageable);

        List<NotificationDTO> dtos = notificationPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        PageResponseDTO<NotificationDTO> response = new PageResponseDTO<>();
        response.setContent(dtos);
        response.setPage(notificationPage.getNumber());
        response.setTotalPages(notificationPage.getTotalPages());
        response.setTotalElements(notificationPage.getTotalElements());
        response.setLast(notificationPage.isLast());

        return response;
    }

    /**
     * Mark notification as read
     */
    public NotificationDTO markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
            updateUserUnreadCount(notification.getUser().getId());
        }

        return convertToDTO(notification);
    }

    /**
     * Mark all notifications as read for user
     */
    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository.findByUserIdAndStatus(
                userId, NotificationStatus.UNREAD);

        for (Notification notification : unreadNotifications) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
        }

        notificationRepository.saveAll(unreadNotifications);
        updateUserUnreadCount(userId);
    }

    /**
     * Archive notification
     */
    public void archiveNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        notification.setStatus(NotificationStatus.ARCHIVED);
        notificationRepository.save(notification);
        updateUserUnreadCount(notification.getUser().getId());
    }

    /**
     * Delete notification
     */
    public void deleteNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        Long userId = notification.getUser().getId();
        notificationRepository.delete(notification);
        updateUserUnreadCount(userId);
    }

    /**
     * Get unread count for user
     */
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    /**
     * Get notification summary for user
     */
    public NotificationSummaryDTO getNotificationSummary(Long userId) {
        // Get all notifications for user
        List<Notification> allNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 1000)).getContent();

        long totalCount = allNotifications.size();
        long unreadCount = allNotifications.stream()
                .filter(n -> n.getStatus() == NotificationStatus.UNREAD)
                .count();
        long criticalCount = allNotifications.stream()
                .filter(n -> n.getStatus() == NotificationStatus.UNREAD &&
                        n.getPriority() == NotificationPriority.CRITICAL)
                .count();
        long highPriorityCount = allNotifications.stream()
                .filter(n -> n.getStatus() == NotificationStatus.UNREAD &&
                        n.getPriority() == NotificationPriority.HIGH)
                .count();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = allNotifications.stream()
                .filter(n -> n.getCreatedAt().isAfter(todayStart))
                .count();

        return NotificationSummaryDTO.builder()
                .totalCount(totalCount)
                .unreadCount(unreadCount)
                .criticalCount(criticalCount)
                .highPriorityCount(highPriorityCount)
                .todayCount(todayCount)
                .build();
    }

    /**
     * Get recent critical notifications
     */
    public List<NotificationDTO> getRecentCriticalNotifications(Long userId) {
        List<NotificationPriority> criticalPriorities = Arrays.asList(
                NotificationPriority.CRITICAL,
                NotificationPriority.HIGH
        );

        List<Notification> criticalNotifications = notificationRepository
                .findByUserIdAndStatusAndPriorityIn(
                        userId,
                        NotificationStatus.UNREAD,
                        criticalPriorities
                );

        return criticalNotifications.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Clean up old notifications
     */
    public int cleanupOldNotifications(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        return notificationRepository.deleteByCreatedAtBeforeAndStatus(
                cutoffDate, NotificationStatus.ARCHIVED);
    }

    /**
     * Clean up archived notifications older than specified days
     */
    public int cleanupArchivedNotifications(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<Notification> archivedNotifications = notificationRepository
                .findByCreatedAtBeforeAndStatus(cutoffDate, NotificationStatus.ARCHIVED);
        
        if (!archivedNotifications.isEmpty()) {
            notificationRepository.deleteAll(archivedNotifications);
            log.info("Deleted {} archived notifications older than {} days", archivedNotifications.size(), daysToKeep);
        }
        
        return archivedNotifications.size();
    }

    /**
     * Clean up expired notifications
     */
    public int cleanupExpiredNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> expiredNotifications = notificationRepository
                .findByExpiresAtBeforeAndStatusNot(now, NotificationStatus.ARCHIVED);
        
        if (!expiredNotifications.isEmpty()) {
            for (Notification notification : expiredNotifications) {
                Long userId = notification.getUser().getId();
                notificationRepository.delete(notification);
                updateUserUnreadCount(userId);
            }
            log.info("Deleted {} expired notifications", expiredNotifications.size());
        }
        
        return expiredNotifications.size();
    }

    /**
     * Clean up old read notifications
     */
    public int cleanupOldReadNotifications(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<Notification> oldReadNotifications = notificationRepository
                .findByReadAtBeforeAndStatus(cutoffDate, NotificationStatus.READ);
        
        if (!oldReadNotifications.isEmpty()) {
            for (Notification notification : oldReadNotifications) {
                Long userId = notification.getUser().getId();
                notificationRepository.delete(notification);
                updateUserUnreadCount(userId);
            }
            log.info("Deleted {} old read notifications older than {} days", oldReadNotifications.size(), daysToKeep);
        }
        
        return oldReadNotifications.size();
    }

    /**
     * Send daily summary to managers
     * FIX: Use enum directly, not string
     */
    public int sendDailySummaryToManagers() {
        // Use enum directly - no string conversion needed
        List<User> managers = userRepository.findByRole(UserRole.HOSPITAL_MANAGER);

        int count = 0;
        for (User manager : managers) {
            try {
                // Create summary notification
                Map<String, String> params = new HashMap<>();
                params.put("date", LocalDate.now().toString());
                params.put("summaryDetails", "Daily system summary");

                // Check if template exists before using it
                if (!templateRepository.existsByCode("DAILY_SUMMARY")) {
                    log.warn("DAILY_SUMMARY template not found, creating custom notification");

                    NotificationCreateDTO dto = NotificationCreateDTO.builder()
                            .userId(manager.getId())
                            .type("DAILY_SUMMARY")
                            .category(NotificationCategory.SYSTEM)
                            .title("Daily Summary")
                            .message("Daily system summary for " + LocalDate.now())
                            .priority(NotificationPriority.LOW)
                            .build();

                    createCustomNotification(dto);
                } else {
                    createNotificationFromTemplate(
                            manager.getId(),
                            "DAILY_SUMMARY",
                            params,
                            null
                    );
                }
                count++;
            } catch (Exception e) {
                log.error("Failed to send daily summary to manager {}: {}",
                        manager.getUsername(), e.getMessage());
            }
        }

        return count;
    }

    /**
     * Escalate critical notifications
     * FIX: Use enum directly for role lookup
     */
    public int escalateCriticalNotifications(int hoursThreshold) {
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(hoursThreshold);

        List<Notification> unescalated = notificationRepository.findUnescalatedCritical(
                thresholdTime, NotificationPriority.CRITICAL, NotificationStatus.UNREAD);

        int escalated = 0;
        for (Notification notification : unescalated) {
            try {
                // Get all managers using enum directly
                List<User> managers = userRepository.findByRole(UserRole.HOSPITAL_MANAGER);

                for (User manager : managers) {
                    // Don't escalate to the same user
                    if (!manager.getId().equals(notification.getUser().getId())) {
                        Map<String, String> params = new HashMap<>();
                        params.put("originalUser", notification.getUser().getUsername());
                        params.put("title", notification.getTitle());
                        params.put("hoursOverdue", String.valueOf(hoursThreshold));

                        // Check if template exists
                        if (!templateRepository.existsByCode("ESCALATION_NOTICE")) {
                            log.warn("ESCALATION_NOTICE template not found, creating custom notification");

                            NotificationCreateDTO dto = NotificationCreateDTO.builder()
                                    .userId(manager.getId())
                                    .type("ESCALATION_NOTICE")
                                    .category(NotificationCategory.SYSTEM)
                                    .title("Critical Notification Escalation")
                                    .message(String.format(
                                            "Critical notification for %s has been unread for %d hours: %s",
                                            params.get("originalUser"),
                                            hoursThreshold,
                                            params.get("title")
                                    ))
                                    .priority(NotificationPriority.CRITICAL)
                                    .actionData(Map.of("originalNotificationId", notification.getId()))
                                    .build();

                            createCustomNotification(dto);
                        } else {
                            createNotificationFromTemplate(
                                    manager.getId(),
                                    "ESCALATION_NOTICE",
                                    params,
                                    Map.of("originalNotificationId", notification.getId())
                            );
                        }
                        escalated++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to escalate notification {}: {}",
                        notification.getId(), e.getMessage());
            }
        }

        return escalated;
    }

    /**
     * Group similar notifications
     */
    public int groupSimilarNotifications() {
        // Implementation for grouping similar notifications
        // This is a placeholder - implement based on your business logic
        return 0;
    }

    /**
     * Update user's unread notification count
     */
    private void updateUserUnreadCount(Long userId) {
        try {
            Long unreadCount = notificationRepository.countByUserIdAndStatus(
                    userId, NotificationStatus.UNREAD);
            userRepository.updateUnreadNotificationCount(userId, unreadCount.intValue());
        } catch (Exception e) {
            log.error("Failed to update unread count for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Generate action URL based on template and data
     */
    private String generateActionUrl(String templateCode, Map<String, Object> actionData) {
        if (actionData == null) return null;

        if (templateCode.startsWith("STOCK") || templateCode.startsWith("LOW_STOCK")) {
            Long productId = (Long) actionData.get("productId");
            return productId != null ? "/products/" + productId : "/products";
        } else if (templateCode.startsWith("EXPIRY") || templateCode.startsWith("BATCH")) {
            Long batchId = (Long) actionData.get("batchId");
            return batchId != null ? "/batch-tracking/" + batchId : "/batch-tracking";
        } else if (templateCode.startsWith("QUARANTINE")) {
            Long recordId = (Long) actionData.get("recordId");
            return recordId != null ? "/quarantine/" + recordId : "/quarantine";
        } else if (templateCode.startsWith("REPORT")) {
            String reportId = (String) actionData.get("reportId");
            return reportId != null ? "/reports/" + reportId : "/reports";
        } else if (templateCode.startsWith("USER")) {
            Long userId = (Long) actionData.get("userId");
            return userId != null ? "/users/" + userId : "/users";
        }

        return null;
    }

    /**
     * Convert Notification entity to DTO
     */
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

    /**
     * Helper method to determine category from template code
     */
    private NotificationCategory determineCategory(String templateCode) {
        if (templateCode.contains("QUARANTINE")) return NotificationCategory.QUARANTINE;
        if (templateCode.contains("BATCH")) return NotificationCategory.BATCH;
        if (templateCode.contains("USER")) return NotificationCategory.USER;
        if (templateCode.contains("STOCK")) return NotificationCategory.STOCK;
        if (templateCode.contains("EXPIRY")) return NotificationCategory.EXPIRY;
        return NotificationCategory.SYSTEM;
    }

    /**
     * Helper method to generate fallback title
     */
    private String generateFallbackTitle(String templateCode, Map<String, String> params) {
        switch (templateCode) {
            case "BATCH_CREATED":
                return "New Batch Created";
            case "QUARANTINE_CREATED":
                return "Item Quarantined";
            case "USER_REGISTERED":
                return "New User Registration";
            default:
                return "System Notification";
        }
    }

    /**
     * Helper method to generate fallback message
     */
    private String generateFallbackMessage(String templateCode, Map<String, String> params) {
        StringBuilder message = new StringBuilder();

        switch (templateCode) {
            case "BATCH_CREATED":
                message.append("New batch ");
                message.append(params.getOrDefault("batchNumber", "N/A"));
                message.append(" created for ");
                message.append(params.getOrDefault("productName", "product"));
                message.append(" with quantity ");
                message.append(params.getOrDefault("quantity", "0"));
                break;
            case "QUARANTINE_CREATED":
                message.append("Product ");
                message.append(params.getOrDefault("productName", "N/A"));
                message.append(" (Batch: ");
                message.append(params.getOrDefault("batchNumber", "N/A"));
                message.append(") has been quarantined. Reason: ");
                message.append(params.getOrDefault("reason", "Not specified"));
                break;
            case "USER_REGISTERED":
                message.append("New user ");
                message.append(params.getOrDefault("username", "N/A"));
                message.append(" (");
                message.append(params.getOrDefault("role", "N/A"));
                message.append(") has been registered");
                break;
            default:
                message.append("Notification: ").append(templateCode);
                params.forEach((k, v) -> message.append("\n").append(k).append(": ").append(v));
        }

        return message.toString();
    }
}