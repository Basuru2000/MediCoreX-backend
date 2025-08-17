package com.medicorex.service.quarantine;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuarantineService {

    private final QuarantineRecordRepository quarantineRepository;
    private final ProductBatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final QuarantineWorkflowService workflowService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Create quarantine record for expired batch
     */
    public QuarantineItemDTO quarantineBatch(Long batchId, String reason, String performedBy) {
        log.info("Quarantining batch {} for reason: {}", batchId, reason);

        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", "id", batchId));

        // Check if batch is already quarantined
        if (batch.getStatus() == ProductBatch.BatchStatus.QUARANTINED) {
            throw new IllegalStateException("Batch is already quarantined");
        }

        // Create quarantine record
        QuarantineRecord record = new QuarantineRecord();
        record.setBatch(batch);
        record.setProduct(batch.getProduct());
        record.setQuantityQuarantined(batch.getQuantity());
        record.setReason(reason);
        record.setQuarantineDate(LocalDate.now());
        record.setQuarantinedBy(performedBy);
        record.setStatus(QuarantineRecord.QuarantineStatus.PENDING_REVIEW);

        // Calculate estimated loss
        BigDecimal estimatedLoss = calculateEstimatedLoss(batch);
        record.setEstimatedLoss(estimatedLoss);

        QuarantineRecord savedRecord = quarantineRepository.save(record);

        // Update batch status
        batch.setStatus(ProductBatch.BatchStatus.QUARANTINED);
        batchRepository.save(batch);

        // Create audit log
        workflowService.logAction(savedRecord.getId(), "QUARANTINE", performedBy,
                null, "PENDING_REVIEW", "Batch quarantined: " + reason);

        // ===== NOTIFICATION TRIGGER =====
        try {
            Map<String, String> params = new HashMap<>();
            params.put("productName", batch.getProduct().getName());
            params.put("batchNumber", batch.getBatchNumber());
            params.put("reason", reason);
            params.put("quantity", String.valueOf(savedRecord.getQuantityQuarantined()));

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("recordId", savedRecord.getId());
            actionData.put("batchId", batchId);
            actionData.put("productId", batch.getProduct().getId());

            notificationService.notifyUsersByRole(
                    Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF"),
                    "QUARANTINE_CREATED",
                    params,
                    actionData
            );

            log.info("Quarantine notification sent for batch {}", batch.getBatchNumber());

        } catch (Exception e) {
            log.error("Failed to send quarantine notification: {}", e.getMessage());
        }

        log.info("Created quarantine record {} for batch {}", savedRecord.getId(), batchId);

        return convertToDTO(savedRecord);
    }

    /**
     * Get all quarantine records with pagination
     */
    @Transactional(readOnly = true)
    public Page<QuarantineItemDTO> getQuarantineRecords(String status, Pageable pageable) {
        Page<QuarantineRecord> records;

        if (status != null && !status.isEmpty()) {
            QuarantineRecord.QuarantineStatus queryStatus =
                    QuarantineRecord.QuarantineStatus.valueOf(status);
            records = quarantineRepository.findByStatus(queryStatus, pageable);
        } else {
            records = quarantineRepository.findAll(pageable);
        }

        return records.map(this::convertToDTO);
    }

    /**
     * Get quarantine record by ID
     */
    @Transactional(readOnly = true)
    public QuarantineItemDTO getQuarantineRecord(Long id) {
        QuarantineRecord record = quarantineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuarantineRecord", "id", id));
        return convertToDTO(record);
    }

    /**
     * Process quarantine action (review, approve, dispose, return)
     */
    public QuarantineItemDTO processAction(QuarantineActionDTO actionDTO) {
        QuarantineRecord record = quarantineRepository.findById(actionDTO.getQuarantineRecordId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QuarantineRecord", "id", actionDTO.getQuarantineRecordId()));

        String previousStatus = record.getStatus().toString();

        switch (actionDTO.getAction().toUpperCase()) {
            case "REVIEW":
                processReview(record, actionDTO);
                break;
            case "APPROVE_DISPOSAL":
                approveDisposal(record, actionDTO);
                break;
            case "APPROVE_RETURN":
                approveReturn(record, actionDTO);
                break;
            case "DISPOSE":
                processDisposal(record, actionDTO);
                break;
            case "RETURN":
                processReturn(record, actionDTO);
                break;
            default:
                throw new IllegalArgumentException("Invalid action: " + actionDTO.getAction());
        }

        QuarantineRecord updatedRecord = quarantineRepository.save(record);

        // Log action
        workflowService.logAction(record.getId(), actionDTO.getAction(),
                actionDTO.getPerformedBy(), previousStatus,
                record.getStatus().toString(), actionDTO.getComments());

        return convertToDTO(updatedRecord);
    }

    /**
     * Get pending review items
     */
    @Transactional(readOnly = true)
    public List<QuarantineItemDTO> getPendingReview() {
        // Use the method without Pageable parameter
        List<QuarantineRecord> pendingRecords = quarantineRepository.findAll().stream()
                .filter(r -> r.getStatus() == QuarantineRecord.QuarantineStatus.PENDING_REVIEW)
                .collect(Collectors.toList());

        // Send notification if there are pending items
        if (!pendingRecords.isEmpty()) {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("count", String.valueOf(pendingRecords.size()));

                notificationService.notifyUsersByRole(
                        Arrays.asList("HOSPITAL_MANAGER"),
                        "QUARANTINE_PENDING",
                        params,
                        Map.of("count", pendingRecords.size())
                );
            } catch (Exception e) {
                log.error("Failed to send pending review notification: {}", e.getMessage());
            }
        }

        return pendingRecords.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get quarantine summary
     */
    @Transactional(readOnly = true)
    public QuarantineSummaryDTO getQuarantineSummary() {
        return QuarantineSummaryDTO.builder()
                .totalItems(quarantineRepository.count())
                .pendingReview(quarantineRepository.countByStatus(
                        QuarantineRecord.QuarantineStatus.PENDING_REVIEW))
                .underReview(quarantineRepository.countByStatus(
                        QuarantineRecord.QuarantineStatus.UNDER_REVIEW))
                .awaitingDisposal(quarantineRepository.countByStatus(
                        QuarantineRecord.QuarantineStatus.APPROVED_FOR_DISPOSAL))
                .awaitingReturn(quarantineRepository.countByStatus(
                        QuarantineRecord.QuarantineStatus.APPROVED_FOR_RETURN))
                .disposed(quarantineRepository.countByStatus(
                        QuarantineRecord.QuarantineStatus.DISPOSED))
                .returned(quarantineRepository.countByStatus(
                        QuarantineRecord.QuarantineStatus.RETURNED))
                .totalQuantity(quarantineRepository.getTotalQuarantinedQuantity())
                .totalEstimatedLoss(quarantineRepository.getTotalEstimatedLoss())
                .build();
    }

    /**
     * Auto-quarantine expired batches (called by scheduled task)
     */
    public void autoQuarantineExpiredBatches() {
        log.info("Running auto-quarantine for expired batches");

        LocalDate today = LocalDate.now();
        List<ProductBatch> expiredBatches = batchRepository.findExpiredActiveBatches(today);

        int quarantinedCount = 0;
        for (ProductBatch batch : expiredBatches) {
            if (batch.getStatus() != ProductBatch.BatchStatus.QUARANTINED) {
                try {
                    quarantineBatch(batch.getId(), "Auto-quarantine: Expired", "SYSTEM");
                    quarantinedCount++;
                } catch (Exception e) {
                    log.error("Failed to quarantine batch {}: {}",
                            batch.getBatchNumber(), e.getMessage());
                }
            }
        }

        log.info("Auto-quarantined {} expired batches", quarantinedCount);
    }

    // Private helper methods

    private void processReview(QuarantineRecord record, QuarantineActionDTO action) {
        if (record.getStatus() != QuarantineRecord.QuarantineStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Record is not pending review");
        }

        record.setStatus(QuarantineRecord.QuarantineStatus.UNDER_REVIEW);
        record.setReviewDate(LocalDateTime.now());
        record.setReviewedBy(action.getPerformedBy());
        record.setNotes(action.getComments());
    }

    private void approveDisposal(QuarantineRecord record, QuarantineActionDTO action) {
        if (record.getStatus() != QuarantineRecord.QuarantineStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Record must be under review before approval");
        }

        record.setStatus(QuarantineRecord.QuarantineStatus.APPROVED_FOR_DISPOSAL);
        record.setNotes(record.getNotes() + "\nApproved for disposal: " + action.getComments());
    }

    private void approveReturn(QuarantineRecord record, QuarantineActionDTO action) {
        if (record.getStatus() != QuarantineRecord.QuarantineStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Record must be under review before approval");
        }

        record.setStatus(QuarantineRecord.QuarantineStatus.APPROVED_FOR_RETURN);
        record.setNotes(record.getNotes() + "\nApproved for return: " + action.getComments());
    }

    private void processDisposal(QuarantineRecord record, QuarantineActionDTO action) {
        if (record.getStatus() != QuarantineRecord.QuarantineStatus.APPROVED_FOR_DISPOSAL) {
            throw new IllegalStateException("Record must be approved for disposal");
        }

        record.setStatus(QuarantineRecord.QuarantineStatus.DISPOSED);
        record.setDisposalDate(LocalDateTime.now());
        record.setDisposalMethod(action.getDisposalMethod());
        record.setDisposalCertificate(action.getDisposalCertificate());

        // Update batch status
        ProductBatch batch = record.getBatch();
        batch.setStatus(ProductBatch.BatchStatus.EXPIRED);
        batch.setQuantity(0);
        batchRepository.save(batch);
    }

    private void processReturn(QuarantineRecord record, QuarantineActionDTO action) {
        if (record.getStatus() != QuarantineRecord.QuarantineStatus.APPROVED_FOR_RETURN) {
            throw new IllegalStateException("Record must be approved for return");
        }

        record.setStatus(QuarantineRecord.QuarantineStatus.RETURNED);
        record.setReturnDate(LocalDateTime.now());
        record.setReturnReference(action.getReturnReference());

        // Update batch status
        ProductBatch batch = record.getBatch();
        batch.setStatus(ProductBatch.BatchStatus.EXPIRED);
        batch.setQuantity(0);
        batchRepository.save(batch);
    }

    private BigDecimal calculateEstimatedLoss(ProductBatch batch) {
        if (batch.getCostPerUnit() != null) {
            return batch.getCostPerUnit()
                    .multiply(BigDecimal.valueOf(batch.getQuantity()));
        }
        return BigDecimal.ZERO;
    }

    private QuarantineItemDTO convertToDTO(QuarantineRecord record) {
        LocalDate today = LocalDate.now();
        long daysInQuarantine = ChronoUnit.DAYS.between(record.getQuarantineDate(), today);

        return QuarantineItemDTO.builder()
                .id(record.getId())
                .productId(record.getProduct().getId())
                .productName(record.getProduct().getName())
                .productCode(record.getProduct().getCode())
                .batchId(record.getBatch().getId())
                .batchNumber(record.getBatch().getBatchNumber())
                .quantityQuarantined(record.getQuantityQuarantined())
                .reason(record.getReason())
                .quarantineDate(record.getQuarantineDate())
                .quarantinedBy(record.getQuarantinedBy())
                .status(record.getStatus().name()) // Convert enum to string
                .estimatedLoss(record.getEstimatedLoss())
                .daysInQuarantine((int) daysInQuarantine)
                .reviewDate(record.getReviewDate())
                .reviewedBy(record.getReviewedBy())
                // Only include fields that exist in your QuarantineRecord entity
                .disposalDate(record.getDisposalDate())
                .disposalMethod(record.getDisposalMethod())
                .disposalCertificate(record.getDisposalCertificate())
                .returnDate(record.getReturnDate())
                .returnReference(record.getReturnReference())
                .build();
    }
}

// ========================================
// IMPORTANT: The pattern is always:
// ========================================
// 1. Services pass List<String> with role names
// 2. NotificationService.notifyUsersByRole() accepts List<String>
// 3. NotificationService internally converts String to UserRole enum
// 4. UserRepository.findByRoleIn() accepts List<UserRole> enum

// DO NOT pass UserRole enums from services to NotificationService
// DO NOT pass strings directly to UserRepository methods