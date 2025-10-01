package com.medicorex.entity;

import com.medicorex.entity.PurchaseOrder.POStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_id", nullable = false)
    private Long poId;

    @Column(name = "po_number", nullable = false, length = 50)
    private String poNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private POStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private POStatus newStatus;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_by_name", nullable = false, length = 100)
    private String changedByName;

    @Column(name = "changed_at", nullable = false)
    @Builder.Default  // ADD THIS ANNOTATION
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String comments;
}