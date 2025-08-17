package com.medicorex.controller;

import com.medicorex.entity.NotificationTemplate;
import com.medicorex.entity.User;
import com.medicorex.repository.NotificationTemplateRepository;
import com.medicorex.repository.UserRepository;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/notifications/debug")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationDebugController {

    private final NotificationTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @GetMapping("/check-system")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> checkNotificationSystem() {
        Map<String, Object> status = new HashMap<>();

        try {
            // 1. Check templates
            List<NotificationTemplate> templates = templateRepository.findAll();
            Map<String, Boolean> templateStatus = new HashMap<>();
            templateStatus.put("BATCH_CREATED", templateRepository.existsByCode("BATCH_CREATED"));
            templateStatus.put("QUARANTINE_CREATED", templateRepository.existsByCode("QUARANTINE_CREATED"));
            templateStatus.put("USER_REGISTERED", templateRepository.existsByCode("USER_REGISTERED"));

            status.put("totalTemplates", templates.size());
            status.put("requiredTemplates", templateStatus);
            status.put("templateCodes", templates.stream()
                    .map(NotificationTemplate::getCode)
                    .sorted()
                    .collect(Collectors.toList()));

            // 2. Check users by role - FIXED enum conversion
            try {
                // Convert string roles to enum
                List<User.UserRole> testRoles = Arrays.asList(
                        User.UserRole.HOSPITAL_MANAGER,
                        User.UserRole.PHARMACY_STAFF
                );
                List<User> managers = userRepository.findByRoleIn(testRoles);

                Map<String, Object> userStats = new HashMap<>();
                userStats.put("totalUsers", userRepository.count());
                userStats.put("managersAndStaff", managers.size());
                userStats.put("usersByRole", managers.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getRole().toString(),
                                Collectors.counting()
                        )));

                status.put("users", userStats);

                // 3. Test notification creation
                if (!managers.isEmpty()) {
                    User testUser = managers.get(0);
                    status.put("testUser", testUser.getUsername() + " (" + testUser.getRole() + ")");

                    // Try to create a test notification
                    Map<String, String> params = new HashMap<>();
                    params.put("productName", "Test Product");
                    params.put("batchNumber", "TEST-001");
                    params.put("quantity", "100");

                    try {
                        notificationService.createNotificationFromTemplate(
                                testUser.getId(),
                                "BATCH_CREATED",
                                params,
                                null
                        );
                        status.put("testNotificationCreation", "SUCCESS");
                    } catch (Exception e) {
                        status.put("testNotificationCreation", "FAILED: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("Error checking users: {}", e.getMessage());
                status.put("userCheckError", e.getMessage());

                // Try alternative method
                List<User> allUsers = userRepository.findAll();
                Map<String, Long> usersByRole = allUsers.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getRole().toString(),
                                Collectors.counting()
                        ));
                status.put("usersByRoleAlternative", usersByRole);
            }

            status.put("status", "System Check Complete");

        } catch (Exception e) {
            log.error("System check failed", e);
            status.put("error", e.getMessage());
            status.put("status", "System Check Failed");
        }

        return ResponseEntity.ok(status);
    }

    @PostMapping("/test-role-notification")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> testRoleNotification(
            @RequestParam String templateCode,
            @RequestParam String role) {

        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, String> params = new HashMap<>();
            params.put("productName", "Test Product");
            params.put("batchNumber", "TEST-BATCH");
            params.put("quantity", "100");
            params.put("username", "testuser");
            params.put("role", role);
            params.put("reason", "Test reason");

            log.info("Testing notification with template: {} for role: {}", templateCode, role);

            notificationService.notifyUsersByRole(
                    Arrays.asList(role),
                    templateCode,
                    params,
                    null
            );

            result.put("status", "Notification triggered");
            result.put("template", templateCode);
            result.put("role", role);
            result.put("message", "Check logs and database for results");

        } catch (Exception e) {
            log.error("Test notification failed", e);
            result.put("status", "Failed");
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-direct-notification")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> testDirectNotification() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Get all managers and staff
            List<User.UserRole> roles = Arrays.asList(
                    User.UserRole.HOSPITAL_MANAGER,
                    User.UserRole.PHARMACY_STAFF
            );

            List<User> users = userRepository.findByRoleIn(roles);
            result.put("foundUsers", users.size());
            result.put("userList", users.stream()
                    .map(u -> u.getUsername() + " (" + u.getRole() + ")")
                    .collect(Collectors.toList()));

            // Create a notification for each user
            for (User user : users) {
                Map<String, String> params = new HashMap<>();
                params.put("message", "Direct test notification");

                notificationService.createNotificationFromTemplate(
                        user.getId(),
                        "TEST_NOTIFICATION",
                        params,
                        null
                );
            }

            result.put("status", "Success");
            result.put("notificationsCreated", users.size());

        } catch (Exception e) {
            log.error("Direct notification test failed", e);
            result.put("status", "Failed");
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}