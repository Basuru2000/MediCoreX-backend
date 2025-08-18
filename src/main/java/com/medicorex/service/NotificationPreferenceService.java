package com.medicorex.service;

import com.medicorex.dto.NotificationCategoryStatusDTO;
import com.medicorex.dto.NotificationPreferenceDTO;
import com.medicorex.dto.NotificationPreferenceUpdateDTO;
import com.medicorex.entity.Notification;
import com.medicorex.entity.NotificationPreference;
import com.medicorex.entity.User;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.NotificationPreferenceRepository;
import com.medicorex.repository.NotificationRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Get preferences for a user (create default if not exists)
     */
    public NotificationPreferenceDTO getUserPreferences(Long userId) {
        NotificationPreference preferences = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        return convertToDTO(preferences);
    }

    /**
     * Update user preferences
     */
    public NotificationPreferenceDTO updateUserPreferences(Long userId, NotificationPreferenceUpdateDTO updateDTO) {
        NotificationPreference preferences = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        // Update global settings if provided
        if (updateDTO.getInAppEnabled() != null) {
            preferences.setInAppEnabled(updateDTO.getInAppEnabled());
        }
        if (updateDTO.getEmailEnabled() != null) {
            preferences.setEmailEnabled(updateDTO.getEmailEnabled());
        }
        if (updateDTO.getSmsEnabled() != null) {
            preferences.setSmsEnabled(updateDTO.getSmsEnabled());
        }

        // Update category preferences
        if (updateDTO.getCategoryPreferences() != null) {
            preferences.setCategoryPreferences(updateDTO.getCategoryPreferences());
        }

        // Update priority threshold
        if (updateDTO.getPriorityThreshold() != null) {
            try {
                preferences.setPriorityThreshold(
                        Notification.NotificationPriority.valueOf(updateDTO.getPriorityThreshold())
                );
            } catch (IllegalArgumentException e) {
                log.warn("Invalid priority threshold: {}", updateDTO.getPriorityThreshold());
            }
        }

        // Update quiet hours
        if (updateDTO.getQuietHours() != null) {
            NotificationPreference.QuietHours quietHours = preferences.getQuietHours();
            if (quietHours == null) {
                quietHours = new NotificationPreference.QuietHours();
            }

            NotificationPreferenceUpdateDTO.QuietHoursUpdateDTO qhUpdate = updateDTO.getQuietHours();
            if (qhUpdate.getEnabled() != null) {
                quietHours.setEnabled(qhUpdate.getEnabled());
            }
            if (qhUpdate.getStartTime() != null) {
                quietHours.setStartTime(qhUpdate.getStartTime());
            }
            if (qhUpdate.getEndTime() != null) {
                quietHours.setEndTime(qhUpdate.getEndTime());
            }
            if (qhUpdate.getTimezone() != null) {
                quietHours.setTimezone(qhUpdate.getTimezone());
            }
            preferences.setQuietHours(quietHours);
        }

        // Update frequency settings
        if (updateDTO.getFrequencySettings() != null) {
            Map<String, NotificationPreference.FrequencyType> frequencySettings = new HashMap<>();
            updateDTO.getFrequencySettings().forEach((key, value) -> {
                try {
                    frequencySettings.put(key, NotificationPreference.FrequencyType.valueOf(value));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid frequency type: {} for category: {}", value, key);
                }
            });
            preferences.setFrequencySettings(frequencySettings);
        }

        // Update digest settings
        if (updateDTO.getDigestEnabled() != null) {
            preferences.setDigestEnabled(updateDTO.getDigestEnabled());
        }
        if (updateDTO.getDigestTime() != null) {
            try {
                preferences.setDigestTime(LocalTime.parse(updateDTO.getDigestTime()));
            } catch (Exception e) {
                log.warn("Invalid digest time format: {}", updateDTO.getDigestTime());
            }
        }

        // Update escalation settings
        if (updateDTO.getEscalationEnabled() != null) {
            preferences.setEscalationEnabled(updateDTO.getEscalationEnabled());
        }
        if (updateDTO.getEscalationContact() != null) {
            preferences.setEscalationContact(updateDTO.getEscalationContact());
        }

        // Update sound/visual settings
        if (updateDTO.getSoundEnabled() != null) {
            preferences.setSoundEnabled(updateDTO.getSoundEnabled());
        }
        if (updateDTO.getDesktopNotifications() != null) {
            preferences.setDesktopNotifications(updateDTO.getDesktopNotifications());
        }

        preferences.setUpdatedBy(getCurrentUsername());
        NotificationPreference saved = preferenceRepository.save(preferences);

        log.info("Updated notification preferences for user {}", userId);
        return convertToDTO(saved);
    }

    /**
     * Check if a notification should be sent based on user preferences
     */
    public boolean shouldSendNotification(Long userId, String category,
                                          Notification.NotificationPriority priority) {
        NotificationPreference preferences = preferenceRepository.findByUserId(userId)
                .orElse(null);

        if (preferences == null) {
            // No preferences set, default to sending
            return true;
        }

        LocalTime currentTime = LocalTime.now();
        boolean shouldSend = preferences.shouldSendNotification(category, priority, currentTime);

        log.debug("Notification check for user {}: category={}, priority={}, shouldSend={}",
                userId, category, priority, shouldSend);

        return shouldSend;
    }

    /**
     * Get category status for a user
     */
    public List<NotificationCategoryStatusDTO> getCategoryStatus(Long userId) {
        NotificationPreference preferences = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        List<NotificationCategoryStatusDTO> statusList = new ArrayList<>();

        // Define all categories with descriptions
        Map<String, String> categoryDescriptions = new HashMap<>();
        categoryDescriptions.put("STOCK", "Stock level alerts and updates");
        categoryDescriptions.put("EXPIRY", "Product expiry notifications");
        categoryDescriptions.put("BATCH", "Batch management updates");
        categoryDescriptions.put("QUARANTINE", "Quarantine status changes");
        categoryDescriptions.put("USER", "User account notifications");
        categoryDescriptions.put("SYSTEM", "System messages and updates");
        categoryDescriptions.put("APPROVAL", "Approval requests and responses");
        categoryDescriptions.put("REPORT", "Report generation notifications");
        categoryDescriptions.put("PROCUREMENT", "Procurement and order updates");

        for (Map.Entry<String, String> entry : categoryDescriptions.entrySet()) {
            String category = entry.getKey();

            // Get unread count for this category
            Long unreadCount = notificationRepository.countByUserIdAndStatusAndCategory(
                    userId,
                    Notification.NotificationStatus.UNREAD,
                    Notification.NotificationCategory.valueOf(category)
            );

            NotificationCategoryStatusDTO status = NotificationCategoryStatusDTO.builder()
                    .category(category)
                    .enabled(preferences.isCategoryEnabled(category))
                    .frequency(preferences.getFrequencySettings() != null
                            ? preferences.getFrequencySettings().get(category).toString()
                            : "IMMEDIATE")
                    .description(entry.getValue())
                    .unreadCount(unreadCount.intValue())
                    .build();

            statusList.add(status);
        }

        return statusList;
    }

    /**
     * Reset preferences to default
     */
    public NotificationPreferenceDTO resetToDefault(Long userId) {
        NotificationPreference preferences = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));

        // Reset to defaults
        preferences.setInAppEnabled(true);
        preferences.setEmailEnabled(false);
        preferences.setSmsEnabled(false);
        preferences.setPriorityThreshold(Notification.NotificationPriority.LOW);
        preferences.setSoundEnabled(true);
        preferences.setDesktopNotifications(true);
        preferences.setDigestEnabled(false);
        preferences.setEscalationEnabled(true);

        // Enable all categories
        Map<String, Boolean> allEnabled = new HashMap<>();
        for (Notification.NotificationCategory cat : Notification.NotificationCategory.values()) {
            allEnabled.put(cat.name(), true);
        }
        preferences.setCategoryPreferences(allEnabled);

        // Set immediate frequency for all
        Map<String, NotificationPreference.FrequencyType> immediateFreq = new HashMap<>();
        for (Notification.NotificationCategory cat : Notification.NotificationCategory.values()) {
            immediateFreq.put(cat.name(), NotificationPreference.FrequencyType.IMMEDIATE);
        }
        preferences.setFrequencySettings(immediateFreq);

        // Disable quiet hours
        preferences.setQuietHours(NotificationPreference.QuietHours.builder()
                .enabled(false)
                .startTime("22:00")
                .endTime("07:00")
                .timezone("UTC")
                .build());

        preferences.setUpdatedBy(getCurrentUsername());
        NotificationPreference saved = preferenceRepository.save(preferences);

        log.info("Reset notification preferences to default for user {}", userId);
        return convertToDTO(saved);
    }

    /**
     * Create default preferences for a user
     */
    private NotificationPreference createDefaultPreferences(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        NotificationPreference preferences = new NotificationPreference();
        preferences.setUser(user);
        preferences.setInAppEnabled(true);
        preferences.setEmailEnabled(false);
        preferences.setSmsEnabled(false);
        preferences.setSoundEnabled(true);
        preferences.setDesktopNotifications(true);
        preferences.setDigestEnabled(false);
        preferences.setDigestTime(LocalTime.of(9, 0));
        preferences.setEscalationEnabled(true);

        // Set priority threshold based on role
        switch (user.getRole()) {
            case HOSPITAL_MANAGER:
                preferences.setPriorityThreshold(Notification.NotificationPriority.LOW);
                break;
            case PHARMACY_STAFF:
                preferences.setPriorityThreshold(Notification.NotificationPriority.MEDIUM);
                break;
            default:
                preferences.setPriorityThreshold(Notification.NotificationPriority.HIGH);
        }

        // Enable all categories by default
        Map<String, Boolean> categoryPrefs = new HashMap<>();
        for (Notification.NotificationCategory cat : Notification.NotificationCategory.values()) {
            categoryPrefs.put(cat.name(), true);
        }
        preferences.setCategoryPreferences(categoryPrefs);

        // Set immediate frequency for all categories
        Map<String, NotificationPreference.FrequencyType> frequencySettings = new HashMap<>();
        for (Notification.NotificationCategory cat : Notification.NotificationCategory.values()) {
            frequencySettings.put(cat.name(), NotificationPreference.FrequencyType.IMMEDIATE);
        }
        preferences.setFrequencySettings(frequencySettings);

        // Default quiet hours (disabled)
        preferences.setQuietHours(NotificationPreference.QuietHours.builder()
                .enabled(false)
                .startTime("22:00")
                .endTime("07:00")
                .timezone("UTC")
                .build());

        preferences.setCreatedBy("SYSTEM");
        return preferenceRepository.save(preferences);
    }

    /**
     * Convert entity to DTO
     */
    private NotificationPreferenceDTO convertToDTO(NotificationPreference entity) {
        NotificationPreferenceDTO.QuietHoursDTO quietHoursDTO = null;
        if (entity.getQuietHours() != null) {
            quietHoursDTO = NotificationPreferenceDTO.QuietHoursDTO.builder()
                    .enabled(entity.getQuietHours().getEnabled())
                    .startTime(entity.getQuietHours().getStartTime())
                    .endTime(entity.getQuietHours().getEndTime())
                    .timezone(entity.getQuietHours().getTimezone())
                    .build();
        }

        Map<String, String> frequencyStrings = null;
        if (entity.getFrequencySettings() != null) {
            frequencyStrings = entity.getFrequencySettings().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().toString()
                    ));
        }

        return NotificationPreferenceDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .username(entity.getUser().getUsername())
                .inAppEnabled(entity.getInAppEnabled())
                .emailEnabled(entity.getEmailEnabled())
                .smsEnabled(entity.getSmsEnabled())
                .categoryPreferences(entity.getCategoryPreferences())
                .priorityThreshold(entity.getPriorityThreshold() != null
                        ? entity.getPriorityThreshold().toString() : "LOW")
                .quietHours(quietHoursDTO)
                .frequencySettings(frequencyStrings)
                .digestEnabled(entity.getDigestEnabled())
                .digestTime(entity.getDigestTime() != null
                        ? entity.getDigestTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "09:00")
                .lastDigestSent(entity.getLastDigestSent())
                .escalationEnabled(entity.getEscalationEnabled())
                .escalationContact(entity.getEscalationContact())
                .soundEnabled(entity.getSoundEnabled())
                .desktopNotifications(entity.getDesktopNotifications())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        // Implementation depends on your security setup
        return "SYSTEM"; // Placeholder
    }
}