package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "quarantine_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantityQuarantined;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(nullable = false)
    private LocalDate quarantineDate;

    @Column(nullable = false, length = 50)
    private String quarantinedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuarantineStatus status = QuarantineStatus.PENDING_REVIEW;

    private LocalDateTime reviewDate;

    @Column(length = 50)
    private String reviewedBy;

    private LocalDateTime disposalDate;

    @Column(length = 100)
    private String disposalMethod;

    @Column(length = 255)
    private String disposalCertificate;

    private LocalDateTime returnDate;

    @Column(length = 100)
    private String returnReference;

    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedLoss;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum QuarantineStatus {
        PENDING_REVIEW,
        UNDER_REVIEW,
        APPROVED_FOR_DISPOSAL,
        APPROVED_FOR_RETURN,
        DISPOSED,
        RETURNED
    }
}