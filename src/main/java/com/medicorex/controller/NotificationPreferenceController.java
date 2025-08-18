package com.medicorex.controller;

import com.medicorex.dto.NotificationCategoryStatusDTO;
import com.medicorex.dto.NotificationPreferenceDTO;
import com.medicorex.dto.NotificationPreferenceUpdateDTO;
import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notification-preferences")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final UserRepository userRepository; // Add this dependency

    /**
     * Get current user's notification preferences
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPreferenceDTO> getMyPreferences(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        NotificationPreferenceDTO preferences = preferenceService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }

    /**
     * Update current user's notification preferences
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPreferenceDTO> updateMyPreferences(
            @Valid @RequestBody NotificationPreferenceUpdateDTO updateDTO,
            Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        NotificationPreferenceDTO updated = preferenceService.updateUserPreferences(userId, updateDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * Get category status for current user
     */
    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationCategoryStatusDTO>> getCategoryStatus(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        List<NotificationCategoryStatusDTO> status = preferenceService.getCategoryStatus(userId);
        return ResponseEntity.ok(status);
    }

    /**
     * Update specific category preference
     */
    @PatchMapping("/categories/{category}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPreferenceDTO> updateCategoryPreference(
            @PathVariable String category,
            @RequestBody Map<String, Object> updates,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);

        NotificationPreferenceUpdateDTO updateDTO = new NotificationPreferenceUpdateDTO();

        // Handle category enable/disable
        if (updates.containsKey("enabled")) {
            NotificationPreferenceDTO current = preferenceService.getUserPreferences(userId);
            Map<String, Boolean> categoryPrefs = current.getCategoryPreferences();
            categoryPrefs.put(category, (Boolean) updates.get("enabled"));
            updateDTO.setCategoryPreferences(categoryPrefs);
        }

        // Handle frequency update
        if (updates.containsKey("frequency")) {
            NotificationPreferenceDTO current = preferenceService.getUserPreferences(userId);
            Map<String, String> frequencySettings = current.getFrequencySettings();
            frequencySettings.put(category, (String) updates.get("frequency"));
            updateDTO.setFrequencySettings(frequencySettings);
        }

        NotificationPreferenceDTO updated = preferenceService.updateUserPreferences(userId, updateDTO);
        return ResponseEntity.ok(updated);
    }

    /**
     * Reset preferences to default
     */
    @PostMapping("/reset")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationPreferenceDTO> resetToDefault(Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        NotificationPreferenceDTO reset = preferenceService.resetToDefault(userId);
        return ResponseEntity.ok(reset);
    }

    /**
     * Test notification with current preferences
     */
    @PostMapping("/test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> testPreferences(
            @RequestBody Map<String, String> testParams,
            Authentication authentication) {

        Long userId = getUserIdFromAuth(authentication);
        String category = testParams.getOrDefault("category", "SYSTEM");
        String priority = testParams.getOrDefault("priority", "MEDIUM");

        boolean shouldSend = preferenceService.shouldSendNotification(
                userId,
                category,
                com.medicorex.entity.Notification.NotificationPriority.valueOf(priority)
        );

        Map<String, Object> result = Map.of(
                "userId", userId,
                "category", category,
                "priority", priority,
                "shouldSend", shouldSend,
                "reason", shouldSend ? "All preference checks passed" : "Blocked by preferences"
        );

        log.info("Preference test for user {}: {}", userId, result);
        return ResponseEntity.ok(result);
    }

    /**
     * Get user preferences by ID (admin only)
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<NotificationPreferenceDTO> getUserPreferences(@PathVariable Long userId) {
        NotificationPreferenceDTO preferences = preferenceService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }

    /**
     * FIXED: Helper method to extract user ID from authentication
     * This now properly retrieves the user ID from the authentication context
     */
    private Long getUserIdFromAuth(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("No authentication found");
        }

        // Get the username from the authentication
        final String username;
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            username = userDetails.getUsername();
        } else {
            username = authentication.getName();
        }

        // Look up the user by username to get the ID
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));

        return user.getId();
    }
}