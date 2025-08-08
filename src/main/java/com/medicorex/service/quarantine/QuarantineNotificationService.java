package com.medicorex.service.quarantine;

import com.medicorex.dto.NotificationCreateDTO;
import com.medicorex.entity.QuarantineRecord;
import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.service.notification.NotificationService;
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
        data.put("action", "APPROVED_FOR_DISPOSAL");

        sendNotificationToRoles(title, message, data,
                List.of("PHARMACY_STAFF"));
    }

    /**
     * Send notification when item is disposed
     */
    public void notifyDisposed(QuarantineRecord record) {
        String title = "Item Disposed";
        String message = String.format(
                "Product %s (Batch: %s) has been disposed. Method: %s, Certificate: %s",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber(),
                record.getDisposalMethod(),
                record.getDisposalCertificate()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("action", "DISPOSED");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER"));
    }

    /**
     * Send escalation notification for overdue items
     */
    public void notifyEscalation(List<QuarantineRecord> overdueRecords) {
        if (overdueRecords.isEmpty()) return;

        String title = "⚠️ Quarantine Escalation Alert";
        String message = String.format(
                "%d quarantine items have exceeded review timeout and require immediate attention.",
                overdueRecords.size()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("count", overdueRecords.size());
        data.put("recordIds", overdueRecords.stream()
                .map(QuarantineRecord::getId)
                .collect(Collectors.toList()));
        data.put("action", "ESCALATION");
        data.put("priority", "HIGH");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER"));
    }

    /**
     * Send notification for successful return
     */
    public void notifyReturned(QuarantineRecord record) {
        String title = "Item Returned to Supplier";
        String message = String.format(
                "Product %s (Batch: %s) has been successfully returned. Reference: %s",
                record.getProduct().getName(),
                record.getBatch().getBatchNumber(),
                record.getReturnReference()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("recordId", record.getId());
        data.put("action", "RETURNED");

        sendNotificationToRoles(title, message, data,
                List.of("HOSPITAL_MANAGER", "PROCUREMENT_OFFICER"));
    }

    /**
     * Send weekly summary notification
     */
    public void sendWeeklySummary(Map<String, Object> summaryData) {
        String title = "Weekly Quarantine Summary";
        String message = String.format(
                "Weekly Summary: %d new items, %d resolved, %d pending. Total loss: $%.2f",
                summaryData.get("newItems"),
                summaryData.get("resolved"),
                summaryData.get("pending"),
                summaryData.get("totalLoss")
        );

        sendNotificationToRoles(title, message, summaryData,
                List.of("HOSPITAL_MANAGER"));
    }

    /**
     * Helper method to send notifications to users with specific roles
     */
    private void sendNotificationToRoles(String title, String message,
                                         Map<String, Object> data, List<String> roles) {
        try {
            List<User> recipients = userRepository.findByRoleIn(roles);

            for (User user : recipients) {
                NotificationCreateDTO notification = NotificationCreateDTO.builder()
                        .userId(user.getId())
                        .title(title)
                        .message(message)
                        .type("QUARANTINE")
                        .priority(determinePriority(data))
                        .data(data)
                        .build();

                notificationService.createNotification(notification);
            }

            log.info("Sent quarantine notification to {} users with roles: {}",
                    recipients.size(), roles);
        } catch (Exception e) {
            log.error("Failed to send quarantine notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Determine notification priority based on action
     */
    private String determinePriority(Map<String, Object> data) {
        String action = (String) data.get("action");
        if (action == null) return "MEDIUM";

        switch (action) {
            case "ESCALATION":
            case "QUARANTINE_CREATED":
                return "HIGH";
            case "PENDING_REVIEW":
            case "APPROVED_FOR_DISPOSAL":
                return "MEDIUM";
            default:
                return "LOW";
        }
    }
}