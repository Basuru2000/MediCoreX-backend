package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goods_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "po_number", nullable = false, length = 50)
    private String poNumber;

    @Column(name = "receipt_date", nullable = false)
    private LocalDateTime receiptDate = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by", nullable = false)
    private User receivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptStatus status = ReceiptStatus.RECEIVED;

    // ✨ NEW FIELDS FOR PHASE 3.2
    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_status", nullable = false, length = 20)
    private AcceptanceStatus acceptanceStatus = AcceptanceStatus.PENDING_APPROVAL;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_checked_by")
    private User qualityCheckedBy;

    @Column(name = "quality_checked_at")
    private LocalDateTime qualityCheckedAt;
    // ✨ END NEW FIELDS

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsReceiptLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper method to add line
    public void addLine(GoodsReceiptLine line) {
        lines.add(line);
        line.setReceipt(this);
    }

    public enum ReceiptStatus {
        RECEIVED,    // Successfully received
        CANCELLED    // Receipt cancelled (rare case)
    }

    // ✨ NEW ENUM FOR PHASE 3.2
    public enum AcceptanceStatus {
        PENDING_APPROVAL,  // Awaiting quality decision
        ACCEPTED,          // Quality approved, inventory updated
        REJECTED           // Quality rejected, not added to inventory
    }
}