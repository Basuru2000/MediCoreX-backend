package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "auto_po_configuration")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoPOConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "schedule_cron", nullable = false, length = 50)
    private String scheduleCron = "0 0 2 * * ?"; // 2 AM daily

    @Column(name = "reorder_multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal reorderMultiplier = new BigDecimal("2.00");

    @Column(name = "days_until_delivery", nullable = false)
    private Integer daysUntilDelivery = 7;

    @Column(name = "min_po_value", precision = 12, scale = 2)
    private BigDecimal minPoValue = new BigDecimal("100.00");

    @Column(name = "only_preferred_suppliers", nullable = false)
    private Boolean onlyPreferredSuppliers = true;

    @Column(name = "auto_approve", nullable = false)
    private Boolean autoApprove = false;

    @Column(name = "notification_enabled", nullable = false)
    private Boolean notificationEnabled = true;

    @Column(name = "notify_roles", length = 200)
    private String notifyRoles = "HOSPITAL_MANAGER,PROCUREMENT_OFFICER";

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus; // SUCCESS, FAILED, PARTIAL

    @Column(name = "last_run_details", columnDefinition = "TEXT")
    private String lastRunDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}