package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "checklist_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private GoodsReceiptChecklist checklist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_item_id", nullable = false)
    private QualityCheckItem checkItem;

    @Column(name = "check_description", nullable = false, length = 500)
    private String checkDescription;

    @Column(nullable = false, length = 100)
    private String answer;

    @Column(name = "is_compliant", nullable = false)
    private Boolean isCompliant = true;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}