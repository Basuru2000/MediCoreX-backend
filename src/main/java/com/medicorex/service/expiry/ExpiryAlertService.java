package com.medicorex.service.expiry;

import com.medicorex.dto.ExpiryAlertDTO;
import com.medicorex.entity.ExpiryAlert;
import com.medicorex.entity.User;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.ExpiryAlertRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExpiryAlertService {

    private final ExpiryAlertRepository alertRepository;
    private final UserRepository userRepository;

    /**
     * Get alerts with pagination and filtering
     */
    @Transactional(readOnly = true)
    public Page<ExpiryAlertDTO> getAlerts(String status, Pageable pageable) {
        Page<ExpiryAlert> alerts;
        
        if (status != null && !status.isEmpty()) {
            ExpiryAlert.AlertStatus alertStatus = ExpiryAlert.AlertStatus.valueOf(status.toUpperCase());
            alerts = alertRepository.findByStatusOrderByAlertDateDesc(alertStatus, pageable);
        } else {
            alerts = alertRepository.findAllByOrderByAlertDateDesc(pageable);
        }
        
        return alerts.map(this::convertToDTO);
    }

    /**
     * Get alert by ID
     */
    @Transactional(readOnly = true)
    public ExpiryAlertDTO getAlertById(Long id) {
        ExpiryAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expiry alert", "id", id));
        return convertToDTO(alert);
    }

    /**
     * Acknowledge alert
     */
    public ExpiryAlertDTO acknowledgeAlert(Long id, String notes) {
        ExpiryAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expiry alert", "id", id));

        User currentUser = getCurrentUser();
        
        alert.setStatus(ExpiryAlert.AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(currentUser);
        alert.setAcknowledgedAt(LocalDateTime.now());
        
        if (notes != null && !notes.trim().isEmpty()) {
            String existingNotes = alert.getNotes() != null ? alert.getNotes() : "";
            alert.setNotes(existingNotes + "\nAcknowledged: " + notes);
        }

        ExpiryAlert savedAlert = alertRepository.save(alert);
        log.info("Alert {} acknowledged by user {}", id, currentUser.getUsername());
        
        return convertToDTO(savedAlert);
    }

    /**
     * Resolve alert
     */
    public ExpiryAlertDTO resolveAlert(Long id, String notes) {
        ExpiryAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expiry alert", "id", id));

        User currentUser = getCurrentUser();
        
        alert.setStatus(ExpiryAlert.AlertStatus.RESOLVED);
        
        if (notes != null && !notes.trim().isEmpty()) {
            String existingNotes = alert.getNotes() != null ? alert.getNotes() : "";
            alert.setNotes(existingNotes + "\nResolved: " + notes);
        }

        ExpiryAlert savedAlert = alertRepository.save(alert);
        log.info("Alert {} resolved by user {}", id, currentUser.getUsername());
        
        return convertToDTO(savedAlert);
    }

    /**
     * Get alerts count by status
     */
    @Transactional(readOnly = true)
    public Long getAlertsCount(String status) {
        if (status != null && !status.isEmpty()) {
            ExpiryAlert.AlertStatus alertStatus = ExpiryAlert.AlertStatus.valueOf(status.toUpperCase());
            return alertRepository.countByStatus(alertStatus);
        } else {
            return alertRepository.count();
        }
    }

    /**
     * Get critical alerts for dashboard
     */
    @Transactional(readOnly = true)
    public List<ExpiryAlertDTO> getCriticalAlerts(int limit) {
        List<ExpiryAlert> alerts = alertRepository.findTop10ByStatusOrderByAlertDateAsc(
            ExpiryAlert.AlertStatus.PENDING);
        
        return alerts.stream()
                .limit(limit)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert entity to DTO
     */
    private ExpiryAlertDTO convertToDTO(ExpiryAlert alert) {
        return ExpiryAlertDTO.builder()
                .id(alert.getId())
                .productId(alert.getProduct().getId())
                .productName(alert.getProduct().getName())
                .productCode(alert.getProduct().getCode())
                .batchId(alert.getBatch() != null ? alert.getBatch().getId() : null)
                .batchNumber(alert.getBatchNumber())
                .configId(alert.getConfig().getId())
                .configName(alert.getConfig().getTierName())
                .severity(alert.getConfig().getSeverity().toString())
                .alertDate(alert.getAlertDate())
                .expiryDate(alert.getExpiryDate())
                .quantityAffected(alert.getQuantityAffected())
                .status(alert.getStatus().toString())
                .acknowledgedBy(alert.getAcknowledgedBy() != null ? 
                    alert.getAcknowledgedBy().getUsername() : null)
                .acknowledgedAt(alert.getAcknowledgedAt())
                .notes(alert.getNotes())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }
}
