package com.medicorex.config;

import com.medicorex.entity.Notification.NotificationPriority;
import com.medicorex.entity.NotificationTemplate;
import com.medicorex.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationTemplateDataLoader {

    private final NotificationTemplateRepository templateRepository;

    @Bean
    @Order(2) // Run after UserDataLoader
    CommandLineRunner initNotificationTemplates() {
        return args -> {
            log.info("Checking notification templates...");

            if (templateRepository.count() == 0) {
                log.info("Loading default notification templates...");

                List<NotificationTemplate> templates = Arrays.asList(
                        // Stock Templates
                        createTemplate("LOW_STOCK", "STOCK", "Low Stock Alert",
                                "Product {{productName}} is running low. Current stock: {{quantity}} units (Minimum: {{minStock}})",
                                NotificationPriority.HIGH),
                        createTemplate("OUT_OF_STOCK", "STOCK", "Out of Stock",
                                "{{productName}} is now out of stock!",
                                NotificationPriority.CRITICAL),
                        createTemplate("STOCK_ADJUSTED", "STOCK", "Stock Adjusted",
                                "Stock for {{productName}} has been {{adjustmentType}} by {{quantity}} units. New quantity: {{newQuantity}}",
                                NotificationPriority.MEDIUM),

                        // Expiry Templates
                        createTemplate("EXPIRY_CRITICAL", "EXPIRY", "Critical Expiry Alert",
                                "{{productName}} (Batch: {{batchNumber}}) expires in {{days}} days!",
                                NotificationPriority.CRITICAL),
                        createTemplate("EXPIRY_WARNING", "EXPIRY", "Expiry Warning",
                                "{{productName}} (Batch: {{batchNumber}}) will expire in {{days}} days",
                                NotificationPriority.HIGH),
                        createTemplate("EXPIRED_PRODUCT", "EXPIRY", "Product Expired",
                                "{{productName}} (Batch: {{batchNumber}}) has expired and needs immediate attention",
                                NotificationPriority.CRITICAL),

                        // Quarantine Templates
                        createTemplate("QUARANTINE_CREATED", "QUARANTINE", "Item Quarantined",
                                "{{productName}} (Batch: {{batchNumber}}) has been quarantined. Reason: {{reason}}",
                                NotificationPriority.HIGH),
                        createTemplate("QUARANTINE_PENDING", "QUARANTINE", "Quarantine Review Required",
                                "{{count}} items are pending review in quarantine",
                                NotificationPriority.HIGH),

                        // User Templates
                        createTemplate("USER_REGISTERED", "USER", "New User Registration",
                                "New user {{username}} ({{role}}) has been registered",
                                NotificationPriority.LOW),
                        createTemplate("USER_ACTIVATED", "USER", "User Activated",
                                "User {{username}} has been activated",
                                NotificationPriority.LOW),

                        // System Templates
                        createTemplate("TEST_NOTIFICATION", "SYSTEM", "Test Notification",
                                "This is a test notification to verify the system is working correctly",
                                NotificationPriority.LOW),
                        createTemplate("SYSTEM_ERROR", "SYSTEM", "System Error",
                                "System error detected: {{errorMessage}}",
                                NotificationPriority.CRITICAL),

                        // Report Templates
                        createTemplate("REPORT_GENERATED", "REPORT", "Report Ready",
                                "Your {{reportType}} report is ready for download",
                                NotificationPriority.LOW),

                        // Batch Templates
                        createTemplate("BATCH_EXPIRING", "BATCH", "Batch Expiring Soon",
                                "Batch {{batchNumber}} of {{productName}} expires in {{days}} days",
                                NotificationPriority.HIGH),
                        createTemplate("BATCH_EXPIRED", "BATCH", "Batch Expired",
                                "Batch {{batchNumber}} of {{productName}} has expired",
                                NotificationPriority.CRITICAL)
                );

                templateRepository.saveAll(templates);
                log.info("Loaded {} notification templates", templates.size());
            } else {
                log.info("Notification templates already exist. Skipping initialization.");
            }
        };
    }

    private NotificationTemplate createTemplate(String code, String category,
                                                String titleTemplate, String messageTemplate, NotificationPriority priority) {
        NotificationTemplate template = new NotificationTemplate();
        template.setCode(code);
        template.setCategory(category);
        template.setTitleTemplate(titleTemplate);
        template.setMessageTemplate(messageTemplate);
        template.setPriority(priority);
        template.setActive(true);
        template.setCreatedAt(LocalDateTime.now());
        return template;
    }
}