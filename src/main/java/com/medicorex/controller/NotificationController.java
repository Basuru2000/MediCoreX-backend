package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.entity.Notification.*;
import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Get current user's notifications
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponseDTO<NotificationDTO>> getMyNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) NotificationPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        Pageable pageable = PageRequest.of(page, size);

        PageResponseDTO<NotificationDTO> notifications =
                notificationService.getUserNotifications(userId, status, category, priority, pageable);

        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationDTO> getNotification(
            @PathVariable Long id,
            Authentication authentication) {

        // In production, verify the notification belongs to the user
        // For now, we'll just mark it as read
        NotificationDTO notification = notificationService.markAsRead(id);
        return ResponseEntity.ok(notification);
    }

    /**
     * Get unread count
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        Long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Get notification summary
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationSummaryDTO> getNotificationSummary(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        NotificationSummaryDTO summary = notificationService.getNotificationSummary(userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get critical notifications
     */
    @GetMapping("/critical")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDTO>> getCriticalNotifications(
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        List<NotificationDTO> critical = notificationService.getRecentCriticalNotifications(userId);
        return ResponseEntity.ok(critical);
    }

    /**
     * Mark notification as read
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationDTO> markAsRead(@PathVariable Long id) {
        NotificationDTO notification = notificationService.markAsRead(id);
        return ResponseEntity.ok(notification);
    }

    /**
     * Mark all notifications as read
     */
    @PutMapping("/mark-all-read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Archive notification
     */
    @PutMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> archiveNotification(@PathVariable Long id) {
        notificationService.archiveNotification(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete notification
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Create custom notification (Admin only)
     */
    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<NotificationDTO> createNotification(
            @Valid @RequestBody NotificationCreateDTO dto) {

        NotificationDTO notification = notificationService.createCustomNotification(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }

    /**
     * Send batch notifications (Admin only)
     */
    @PostMapping("/batch")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> sendBatchNotifications(
            @Valid @RequestBody NotificationBatchDTO dto) {

        // For custom notifications
        if (dto.getTemplateCode() == null) {
            for (Long userId : dto.getUserIds()) {
                NotificationCreateDTO createDto = NotificationCreateDTO.builder()
                        .userId(userId)
                        .type("SYSTEM_ANNOUNCEMENT")
                        .category(NotificationCategory.SYSTEM)
                        .title(dto.getCustomTitle())
                        .message(dto.getCustomMessage())
                        .priority(NotificationPriority.MEDIUM)
                        .build();
                notificationService.createCustomNotification(createDto);
            }
        } else {
            // Template-based notifications
            for (Long userId : dto.getUserIds()) {
                notificationService.createNotificationFromTemplate(
                        userId,
                        dto.getTemplateCode(),
                        dto.getParams(),
                        dto.getActionData()
                );
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Notifications sent successfully",
                "count", String.valueOf(dto.getUserIds().size())
        ));
    }

    /**
     * Test notification (Dev only)
     */
    @PostMapping("/test")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<NotificationDTO> testNotification(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        NotificationCreateDTO dto = NotificationCreateDTO.builder()
                .userId(userId)
                .type("TEST_NOTIFICATION")
                .category(NotificationCategory.SYSTEM)
                .title("Test Notification")
                .message("This is a test notification to verify the system is working correctly.")
                .priority(NotificationPriority.LOW)
                .build();

        NotificationDTO notification = notificationService.createCustomNotification(dto);
        return ResponseEntity.ok(notification);
    }

    /**
     * Helper method to get current user ID
     * UPDATED: Throws exceptions instead of returning null
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("No authentication found");
        }

        final String username;
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            username = userDetails.getUsername();
        } else {
            username = authentication.getName();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        return user.getId();
    }
}