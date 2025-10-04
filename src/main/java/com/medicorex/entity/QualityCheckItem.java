package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quality_check_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private QualityChecklistTemplate template;

    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Column(name = "check_description", nullable = false, length = 500)
    private String checkDescription;

    @Column(name = "check_type", nullable = false, length = 20)
    private String checkType = "YES_NO"; // YES_NO, TEXT, NUMERIC, PASS_FAIL

    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory = false;

    @Column(name = "expected_value", length = 100)
    private String expectedValue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}