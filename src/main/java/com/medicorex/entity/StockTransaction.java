package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "before_quantity")
    private Integer beforeQuantity;

    @Column(name = "after_quantity")
    private Integer afterQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}