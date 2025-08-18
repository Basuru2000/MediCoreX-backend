package com.medicorex.config;

import com.medicorex.entity.Notification;
import com.medicorex.entity.NotificationPreference;
import com.medicorex.entity.User;
import com.medicorex.repository.NotificationPreferenceRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationPreferenceDataLoader {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Bean
    @Order(3) // Run after UserDataLoader and NotificationTemplateDataLoader
    CommandLineRunner initNotificationPreferences() {
        return args -> {
            log.info("Checking notification preferences...");

            List<User> users = userRepository.findAll();
            int created = 0;

            for (User user : users) {
                if (!preferenceRepository.existsByUserId(user.getId())) {
                    NotificationPreference preference = createDefaultPreference(user);
                    preferenceRepository.save(preference);
                    created++;
                    log.info("Created default preferences for user: {}", user.getUsername());
                }
            }

            if (created > 0) {
                log.info("Created {} default notification preferences", created);
            } else {
                log.info("All users have notification preferences configured");
            }
        };
    }

    private NotificationPreference createDefaultPreference(User user) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUser(user);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(false);
        preference.setSmsEnabled(false);
        preference.setSoundEnabled(true);
        preference.setDesktopNotifications(true);
        preference.setDigestEnabled(false);
        preference.setDigestTime(LocalTime.of(9, 0));
        preference.setEscalationEnabled(true);

        // Set priority threshold based on role
        switch (user.getRole()) {
            case HOSPITAL_MANAGER:
                preference.setPriorityThreshold(Notification.NotificationPriority.LOW);
                break;
            case PHARMACY_STAFF:
                preference.setPriorityThreshold(Notification.NotificationPriority.MEDIUM);
                break;
            case PROCUREMENT_OFFICER:
                preference.setPriorityThreshold(Notification.NotificationPriority.HIGH);
                break;
            default:
                preference.setPriorityThreshold(Notification.NotificationPriority.MEDIUM);
        }

        // Enable all categories
        Map<String, Boolean> categoryPrefs = new HashMap<>();
        for (Notification.NotificationCategory cat : Notification.NotificationCategory.values()) {
            categoryPrefs.put(cat.name(), true);
        }
        preference.setCategoryPreferences(categoryPrefs);

        // Set immediate frequency for all
        Map<String, NotificationPreference.FrequencyType> frequencySettings = new HashMap<>();
        for (Notification.NotificationCategory cat : Notification.NotificationCategory.values()) {
            // Reports can be daily digest by default
            if (cat == Notification.NotificationCategory.REPORT) {
                frequencySettings.put(cat.name(), NotificationPreference.FrequencyType.DAILY_DIGEST);
            } else {
                frequencySettings.put(cat.name(), NotificationPreference.FrequencyType.IMMEDIATE);
            }
        }
        preference.setFrequencySettings(frequencySettings);

        // Default quiet hours (disabled)
        preference.setQuietHours(NotificationPreference.QuietHours.builder()
                .enabled(false)
                .startTime("22:00")
                .endTime("07:00")
                .timezone("UTC")
                .build());

        preference.setCreatedBy("SYSTEM");
        return preference;
    }
}
