package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expiry_check_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryCheckLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate checkDate;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckStatus status = CheckStatus.RUNNING;

    @Column(nullable = false)
    private Integer productsChecked = 0;

    @Column(nullable = false)
    private Integer alertsGenerated = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Long executionTimeMs;

    @Column(length = 50, nullable = false)
    private String createdBy = "SYSTEM";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CheckStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }
}