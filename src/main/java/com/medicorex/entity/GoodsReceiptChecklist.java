package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goods_receipt_checklists")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private QualityChecklistTemplate template;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by", nullable = false)
    private User completedBy;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt = LocalDateTime.now();

    @Column(name = "overall_result", nullable = false, length = 20)
    private String overallResult; // PASS, FAIL, CONDITIONAL

    @Column(name = "inspector_notes", columnDefinition = "TEXT")
    private String inspectorNotes;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChecklistAnswer> answers = new ArrayList<>();

    // Helper method to add answer
    public void addAnswer(ChecklistAnswer answer) {
        answers.add(answer);
        answer.setChecklist(this);
    }
}