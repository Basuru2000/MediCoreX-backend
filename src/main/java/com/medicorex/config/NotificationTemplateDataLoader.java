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
                                "{{productName}} (Batch: {{batchNumber}}) will expire on {{expiryDate}}",
                                NotificationPriority.HIGH),
                        createTemplate("EXPIRY_NOTICE", "EXPIRY", "Expiry Notice",
                                "{{productName}} (Batch: {{batchNumber}}) expires in {{days}} days",
                                NotificationPriority.MEDIUM),

                        // Quarantine Templates - FIX: Changed from QUARANTINE_NEW to QUARANTINE_CREATED
                        createTemplate("QUARANTINE_CREATED", "QUARANTINE", "Item Quarantined",
                                "Product {{productName}} (Batch: {{batchNumber}}) has been quarantined. Reason: {{reason}}",
                                NotificationPriority.HIGH),
                        createTemplate("QUARANTINE_APPROVED_DISPOSAL", "QUARANTINE", "Quarantine Item Approved for Disposal",
                                "Product {{productName}} has been approved for disposal. Please proceed with disposal process.",
                                NotificationPriority.HIGH),
                        createTemplate("QUARANTINE_APPROVED_RETURN", "QUARANTINE", "Quarantine Item Approved for Return",
                                "Product {{productName}} has been approved for return to supplier.",
                                NotificationPriority.MEDIUM),
                        createTemplate("QUARANTINE_DISPOSED", "QUARANTINE", "Item Disposed",
                                "Product {{productName}} (Batch: {{batchNumber}}) has been disposed. Reason: {{reason}}",
                                NotificationPriority.MEDIUM),
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
                        createTemplate("USER_DEACTIVATED", "USER", "User Deactivated",
                                "User {{username}} has been deactivated",
                                NotificationPriority.MEDIUM),
                        createTemplate("USER_ROLE_CHANGED", "USER", "Role Updated",
                                "Your role has been updated to {{role}}",
                                NotificationPriority.MEDIUM),

                        // Batch Templates - FIX: Added BATCH_CREATED template
                        createTemplate("BATCH_CREATED", "BATCH", "New Batch Created",
                                "New batch {{batchNumber}} created for {{productName}} with quantity {{quantity}}",
                                NotificationPriority.MEDIUM),
                        createTemplate("BATCH_EXPIRING", "BATCH", "Batch Expiring Soon",
                                "Batch {{batchNumber}} of {{productName}} expires in {{days}} days",
                                NotificationPriority.HIGH),
                        createTemplate("BATCH_EXPIRED", "BATCH", "Batch Expired",
                                "Batch {{batchNumber}} of {{productName}} has expired",
                                NotificationPriority.CRITICAL),
                        createTemplate("BATCH_CONSUMED", "BATCH", "Batch Stock Consumed",
                                "{{quantity}} units consumed from batch {{batchNumber}} of {{productName}}",
                                NotificationPriority.LOW),
                        createTemplate("BATCH_ADJUSTED", "BATCH", "Batch Stock Adjusted",
                                "Batch {{batchNumber}} stock adjusted: {{adjustment}}",
                                NotificationPriority.MEDIUM),

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
                                NotificationPriority.LOW)
                );

                templateRepository.saveAll(templates);
                log.info("Loaded {} notification templates", templates.size());
            } else {
                log.info("Notification templates already exist. Checking for missing templates...");

                // Check and add missing templates
                if (!templateRepository.existsByCode("BATCH_CREATED")) {
                    NotificationTemplate batchCreated = createTemplate("BATCH_CREATED", "BATCH",
                            "New Batch Created",
                            "New batch {{batchNumber}} created for {{productName}} with quantity {{quantity}}",
                            NotificationPriority.MEDIUM);
                    templateRepository.save(batchCreated);
                    log.info("Added missing BATCH_CREATED template");
                }

                // Fix template code if QUARANTINE_NEW exists but not QUARANTINE_CREATED
                templateRepository.findByCode("QUARANTINE_NEW").ifPresent(template -> {
                    template.setCode("QUARANTINE_CREATED");
                    templateRepository.save(template);
                    log.info("Updated QUARANTINE_NEW to QUARANTINE_CREATED");
                });
            }
        };
    }

    private NotificationTemplate createTemplate(String code, String category,
                                                String titleTemplate, String messageTemplate,
                                                NotificationPriority priority) {
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