package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quarantine_action_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quarantine_record_id", nullable = false)
    private Long quarantineRecordId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 50)
    private String performedBy;

    @Column(nullable = false)
    private LocalDateTime performedAt = LocalDateTime.now();

    @Column(length = 50)
    private String previousStatus;

    @Column(length = 50)
    private String newStatus;

    @Column(columnDefinition = "TEXT")
    private String comments;
}