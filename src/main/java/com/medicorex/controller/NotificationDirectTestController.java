package com.medicorex.controller;

import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/test/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationDirectTestController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Test batch creation notification
     */
    @PostMapping("/test-batch-created")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> testBatchCreatedNotification() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Prepare parameters
            Map<String, String> params = new HashMap<>();
            params.put("productName", "Test Product");
            params.put("batchNumber", "BATCH-TEST-001");
            params.put("quantity", "100");
            params.put("expiryDate", "2025-12-31");

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("batchId", 1L);
            actionData.put("productId", 1L);

            // Use SYNC version for testing
            log.info("Testing BATCH_CREATED notification...");
            notificationService.notifyUsersByRoleSync(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "BATCH_CREATED",
                    params,
                    actionData
            );

            result.put("status", "SUCCESS");
            result.put("message", "BATCH_CREATED notification test completed");

        } catch (Exception e) {
            log.error("Test failed", e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Test quarantine notification
     */
    @PostMapping("/test-quarantine-created")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> testQuarantineNotification() {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, String> params = new HashMap<>();
            params.put("productName", "Test Product");
            params.put("batchNumber", "BATCH-001");
            params.put("reason", "Product expired");
            params.put("quantity", "50");

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("recordId", 1L);
            actionData.put("batchId", 1L);
            actionData.put("productId", 1L);

            log.info("Testing QUARANTINE_CREATED notification...");
            notificationService.notifyUsersByRoleSync(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "QUARANTINE_CREATED",
                    params,
                    actionData
            );

            result.put("status", "SUCCESS");
            result.put("message", "QUARANTINE_CREATED notification test completed");

        } catch (Exception e) {
            log.error("Test failed", e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Test user registration notification
     */
    @PostMapping("/test-user-registered")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> testUserRegisteredNotification() {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, String> params = new HashMap<>();
            params.put("username", "newuser");
            params.put("role", "PHARMACY_STAFF");

            log.info("Testing USER_REGISTERED notification...");
            notificationService.notifyUsersByRoleSync(
                    Arrays.asList("HOSPITAL_MANAGER"),
                    "USER_REGISTERED",
                    params,
                    Map.of("userId", 999L)
            );

            result.put("status", "SUCCESS");
            result.put("message", "USER_REGISTERED notification test completed");

        } catch (Exception e) {
            log.error("Test failed", e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Show all users and their roles
     */
    @GetMapping("/show-users")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> showUsers() {
        Map<String, Object> result = new HashMap<>();

        try {
            List<User> allUsers = userRepository.findAll();
            List<Map<String, Object>> userList = new ArrayList<>();

            for (User user : allUsers) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("role", user.getRole().toString());
                userInfo.put("roleClass", user.getRole().getClass().getName());
                userInfo.put("active", user.getActive());
                userList.add(userInfo);
            }

            result.put("users", userList);
            result.put("totalCount", allUsers.size());

            // Test finding by role
            List<User.UserRole> testRoles = Arrays.asList(
                    User.UserRole.HOSPITAL_MANAGER,
                    User.UserRole.PHARMACY_STAFF
            );
            List<User> foundByRole = userRepository.findByRoleIn(testRoles);
            result.put("foundByRole", foundByRole.size());

        } catch (Exception e) {
            log.error("Show users failed", e);
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}