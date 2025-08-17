package com.medicorex.service.quarantine;

import com.medicorex.dto.NotificationCreateDTO;
import com.medicorex.entity.Notification;
import com.medicorex.entity.Notification.NotificationCategory;
import com.medicorex.entity.Notification.NotificationPriority;
import com.medicorex.entity.QuarantineRecord;
import com.medicorex.entity.User;
import com.medicorex.entity.User.UserRole;
import com.medicorex.repository.UserRepository;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuarantineNotificationService {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Send notification when item is quarantined
     */
    public void notifyQuarantineCreated(QuarantineRecord record) {
        String title = "Item Quarantined";
        String message = String.format(
                "Product %s (Batch: %s) has been quarantined. Quantity: %d, Reason: %s",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber(),
                record.getQuantityQuarantined(),
                record.getReason()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("productId", record.getProduct().getId());
        data.put("batchId", record.getBatch().getId());
        data.put("action", "QUARANTINE_CREATED");

        // Use List<String> for roles - NotificationService will convert them
        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PHARMACY_STAFF"));
    }

    /**
     * Send notification for pending review items
     */
    public void notifyPendingReview(List<QuarantineRecord> pendingRecords) {
        if (pendingRecords.isEmpty()) return;

        String title = "Quarantine Items Pending Review";
        String message = String.format(
                "%d items are pending review in quarantine. Immediate action required.",
                pendingRecords.size()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("count", pendingRecords.size());
        data.put("recordIds", pendingRecords.stream()
                .map(QuarantineRecord::getId)
                .collect(Collectors.toList()));
        data.put("action", "PENDING_REVIEW");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PHARMACY_STAFF"));
    }

    /**
     * Send notification when item is approved for disposal
     */
    public void notifyApprovedForDisposal(QuarantineRecord record) {
        String title = "Item Approved for Disposal";
        String message = String.format(
                "Product %s (Batch: %s) has been approved for disposal. Please proceed with disposal process.",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("productId", record.getProduct().getId());
        data.put("batchId", record.getBatch().getId());
        data.put("action", "APPROVED_FOR_DISPOSAL");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PHARMACY_STAFF"));
    }

    /**
     * Send notification when item is approved for return
     */
    public void notifyApprovedForReturn(QuarantineRecord record) {
        String title = "Item Approved for Return";
        String message = String.format(
                "Product %s (Batch: %s) has been approved for return to supplier.",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("productId", record.getProduct().getId());
        data.put("batchId", record.getBatch().getId());
        data.put("action", "APPROVED_FOR_RETURN");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PROCUREMENT_OFFICER"));
    }

    /**
     * Send notification when item is disposed
     */
    public void notifyItemDisposed(QuarantineRecord record, String disposalMethod) {
        String title = "Item Disposed";
        String message = String.format(
                "Product %s (Batch: %s) has been disposed using method: %s",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber(),
                disposalMethod
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("productId", record.getProduct().getId());
        data.put("batchId", record.getBatch().getId());
        data.put("disposalMethod", disposalMethod);
        data.put("action", "ITEM_DISPOSED");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PHARMACY_STAFF"));
    }

    /**
     * Send notification when item is returned to supplier
     */
    public void notifyItemReturned(QuarantineRecord record) {
        String title = "Item Returned to Supplier";
        String message = String.format(
                "Product %s (Batch: %s) has been successfully returned to supplier.",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("productId", record.getProduct().getId());
        data.put("batchId", record.getBatch().getId());
        data.put("action", "ITEM_RETURNED");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PROCUREMENT_OFFICER"));
    }

    /**
     * Send notification for escalation
     */
    public void notifyEscalation(List<QuarantineRecord> escalatedRecords) {
        if (escalatedRecords.isEmpty()) return;

        String title = "Quarantine Items Require Urgent Attention";
        String message = String.format(
                "%d quarantine items have been pending for over 3 days and require immediate attention.",
                escalatedRecords.size()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("count", escalatedRecords.size());
        data.put("recordIds", escalatedRecords.stream()
                .map(QuarantineRecord::getId)
                .collect(Collectors.toList()));
        data.put("action", "ESCALATION");

        // Only notify managers for escalations
        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER"));
    }

    /**
     * Helper method to send notifications to multiple roles
     * This method accepts List<String> and lets NotificationService handle conversion
     */
    private void sendNotificationToRoles(String title, String message,
                                         Map<String, Object> data, List<String> roleStrings) {
        try {
            // Create parameters for template-based notification
            Map<String, String> params = new HashMap<>();
            params.put("title", title);
            params.put("message", message);

            // Use the NotificationService's notifyUsersByRole method which accepts List<String>
            notificationService.notifyUsersByRole(
                    roleStrings,  // Pass List<String> directly
                    "QUARANTINE_NOTIFICATION",
                    params,
                    data
            );

            log.info("Quarantine notification sent to roles: {}", roleStrings);

        } catch (Exception e) {
            log.error("Failed to send quarantine notification: {}", e.getMessage(), e);

            // Fallback: Try to create custom notifications directly
            try {
                // Convert strings to enums for direct user lookup
                List<UserRole> roleEnums = roleStrings.stream()
                        .map(role -> UserRole.valueOf(role))
                        .collect(Collectors.toList());

                List<User> users = userRepository.findByRoleIn(roleEnums);

                for (User user : users) {
                    try {
                        NotificationCreateDTO dto = NotificationCreateDTO.builder()
                                .userId(user.getId())
                                .type("QUARANTINE_NOTIFICATION")
                                .category(NotificationCategory.QUARANTINE)
                                .title(title)
                                .message(message)
                                .priority(NotificationPriority.HIGH)
                                .actionData(data)
                                .build();

                        notificationService.createCustomNotification(dto);
                        log.debug("Created custom notification for user: {}", user.getUsername());
                    } catch (Exception ex) {
                        log.error("Failed to create notification for user {}: {}",
                                user.getUsername(), ex.getMessage());
                    }
                }
            } catch (Exception fallbackError) {
                log.error("Fallback notification creation also failed: {}",
                        fallbackError.getMessage());
            }
        }
    }
}