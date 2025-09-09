package com.medicorex.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    @JsonIgnore
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "supplier_product_code", length = 100)
    private String supplierProductCode;

    @Column(name = "supplier_product_name", length = 200)
    private String supplierProductName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 3)
    private String currency = "USD";

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(name = "bulk_discount_percentage", precision = 5, scale = 2)
    private BigDecimal bulkDiscountPercentage = BigDecimal.ZERO;

    @Column(name = "bulk_quantity_threshold")
    private Integer bulkQuantityThreshold = 0;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays = 0;

    @Column(name = "min_order_quantity")
    private Integer minOrderQuantity = 1;

    @Column(name = "max_order_quantity")
    private Integer maxOrderQuantity;

    @Column(name = "is_preferred")
    private Boolean isPreferred = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_price_update")
    private LocalDate lastPriceUpdate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Calculate effective price based on quantity
    public BigDecimal getEffectivePrice(int quantity) {
        BigDecimal basePrice = unitPrice;

        // Apply regular discount
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discount = basePrice.multiply(discountPercentage).divide(new BigDecimal(100));
            basePrice = basePrice.subtract(discount);
        }

        // Apply bulk discount if applicable
        if (quantity >= bulkQuantityThreshold && bulkDiscountPercentage != null &&
                bulkDiscountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal bulkDiscount = basePrice.multiply(bulkDiscountPercentage).divide(new BigDecimal(100));
            basePrice = basePrice.subtract(bulkDiscount);
        }

        return basePrice.setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}