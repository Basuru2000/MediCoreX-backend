package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_product_id")
    private SupplierProduct supplierProduct;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "received_quantity")
    private Integer receivedQuantity = 0;

    // ✨ NEW FIELD FOR PARTIAL RECEIPT TRACKING
    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Helper method to calculate line total
    public void calculateLineTotal() {
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discountAmount = subtotal.multiply(discountPercentage != null ? discountPercentage : BigDecimal.ZERO).divide(BigDecimal.valueOf(100));
        BigDecimal afterDiscount = subtotal.subtract(discountAmount);
        BigDecimal taxAmount = afterDiscount.multiply(taxPercentage != null ? taxPercentage : BigDecimal.ZERO).divide(BigDecimal.valueOf(100));

        this.lineTotal = afterDiscount.add(taxAmount);
    }

    /**
     * Update quantities after receiving items
     */
    public void receiveQuantity(int receivedQty) {
        if (receivedQty < 0) {
            throw new IllegalArgumentException("Received quantity cannot be negative");
        }
        if (this.receivedQuantity + receivedQty > this.quantity) {
            throw new IllegalArgumentException("Total received quantity cannot exceed ordered quantity");
        }
        this.receivedQuantity += receivedQty;
        this.remainingQuantity = this.quantity - this.receivedQuantity;
    }

    /**
     * Check if line is fully received
     */
    public boolean isFullyReceived() {
        return this.remainingQuantity == 0 && this.receivedQuantity.equals(this.quantity);
    }

    /**
     * Check if line is partially received
     */
    public boolean isPartiallyReceived() {
        return this.receivedQuantity > 0 && this.remainingQuantity > 0;
    }

    /**
     * Initialize remaining quantity (called when PO is created)
     */
    @PrePersist
    public void initializeRemainingQuantity() {
        if (this.remainingQuantity == null || this.remainingQuantity == 0) {
            this.remainingQuantity = this.quantity - (this.receivedQuantity != null ? this.receivedQuantity : 0);
        }
    }
}