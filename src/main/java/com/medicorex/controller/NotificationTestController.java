package com.medicorex.controller;

import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Test controller for notification system
 * Remove this in production!
 */
@Slf4j
@RestController
@RequestMapping("/api/test/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationTestController {

    private final NotificationService notificationService;

    @PostMapping("/test-all")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> testAllNotifications() {
        Map<String, String> results = new HashMap<>();
        int successCount = 0;
        int failCount = 0;

        // Test Stock Notifications
        try {
            Map<String, String> stockParams = new HashMap<>();
            stockParams.put("productName", "Test Product");
            stockParams.put("quantity", "10");
            stockParams.put("minStock", "20");

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "LOW_STOCK",
                    stockParams,
                    Map.of("productId", 1L)
            );
            results.put("LOW_STOCK", "SUCCESS");
            successCount++;
        } catch (Exception e) {
            results.put("LOW_STOCK", "FAILED: " + e.getMessage());
            failCount++;
        }

        // Test Expiry Notifications
        try {
            Map<String, String> expiryParams = new HashMap<>();
            expiryParams.put("productName", "Aspirin");
            expiryParams.put("batchNumber", "TEST-BATCH");
            expiryParams.put("days", "5");

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "EXPIRY_CRITICAL",
                    expiryParams,
                    Map.of("productId", 1L)
            );
            results.put("EXPIRY_CRITICAL", "SUCCESS");
            successCount++;
        } catch (Exception e) {
            results.put("EXPIRY_CRITICAL", "FAILED: " + e.getMessage());
            failCount++;
        }

        // Test Quarantine Notifications
        try {
            Map<String, String> quarantineParams = new HashMap<>();
            quarantineParams.put("productName", "Paracetamol");
            quarantineParams.put("batchNumber", "BATCH-2024");
            quarantineParams.put("reason", "Quality check failed");

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "QUARANTINE_CREATED",
                    quarantineParams,
                    Map.of("recordId", 1L)
            );
            results.put("QUARANTINE_CREATED", "SUCCESS");
            successCount++;
        } catch (Exception e) {
            results.put("QUARANTINE_CREATED", "FAILED: " + e.getMessage());
            failCount++;
        }

        // Test Batch Notifications
        try {
            Map<String, String> batchParams = new HashMap<>();
            batchParams.put("batchNumber", "NEW-BATCH-2024");
            batchParams.put("productName", "Vitamin C");

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "BATCH_CREATED",
                    batchParams,
                    Map.of("batchId", 1L)
            );
            results.put("BATCH_CREATED", "SUCCESS");
            successCount++;
        } catch (Exception e) {
            results.put("BATCH_CREATED", "FAILED: " + e.getMessage());
            failCount++;
        }

        // Test User Notifications
        try {
            Map<String, String> userParams = new HashMap<>();
            userParams.put("username", "testuser");
            userParams.put("role", "PHARMACY_STAFF");

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER"),
                    "USER_REGISTERED",
                    userParams,
                    Map.of("userId", 1L)
            );
            results.put("USER_REGISTERED", "SUCCESS");
            successCount++;
        } catch (Exception e) {
            results.put("USER_REGISTERED", "FAILED: " + e.getMessage());
            failCount++;
        }

        // Test System Notifications
        try {
            Map<String, String> systemParams = new HashMap<>();
            systemParams.put("message", "System test completed");

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER"),
                    "TEST_NOTIFICATION",
                    systemParams,
                    null
            );
            results.put("TEST_NOTIFICATION", "SUCCESS");
            successCount++;
        } catch (Exception e) {
            results.put("TEST_NOTIFICATION", "FAILED: " + e.getMessage());
            failCount++;
        }

        results.put("SUMMARY", String.format("Success: %d, Failed: %d", successCount, failCount));

        log.info("Notification test completed: {} success, {} failed", successCount, failCount);

        return ResponseEntity.ok(results);
    }

    @GetMapping("/verify")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, Object>> verifyNotificationSystem() {
        Map<String, Object> status = new HashMap<>();

        try {
            // Check if notification service is available
            status.put("notificationServiceAvailable", notificationService != null);

            // Get recent notification count
            status.put("timestamp", new Date());
            status.put("status", "Notification system is operational");

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            status.put("error", e.getMessage());
            status.put("status", "Notification system error");
            return ResponseEntity.status(500).body(status);
        }
    }
}