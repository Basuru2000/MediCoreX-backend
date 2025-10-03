package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String code;

    @Column(unique = true)
    private String barcode;

    @Column(columnDefinition = "TEXT")
    private String qrCode;

    private String description;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(nullable = false)
    private Integer minStockLevel;

    @Column(nullable = false)
    private String unit; // e.g., "tablets", "bottles", "boxes"

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    // NEW: Purchase price tracking (weighted average cost)
    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    private LocalDate expiryDate;

    private String batchNumber;

    private String manufacturer;

    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    // Stock level thresholds
    @Column(name = "min_stock")
    private Integer minStock = 10; // Default minimum stock level

    @Column(name = "max_stock")
    private Integer maxStock = 1000; // Default maximum stock level

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "last_stock_check")
    private LocalDateTime lastStockCheck;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}