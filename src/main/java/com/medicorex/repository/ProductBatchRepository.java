package com.medicorex.repository;

import com.medicorex.entity.ProductBatch;
import com.medicorex.entity.ProductBatch.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {

    // Find all batches for a product
    List<ProductBatch> findByProductIdOrderByExpiryDateAsc(Long productId);

    // Find active batches for a product (FIFO order)
    List<ProductBatch> findByProductIdAndStatusOrderByExpiryDateAsc(Long productId, BatchStatus status);

    // Find by product and batch number
    Optional<ProductBatch> findByProductIdAndBatchNumber(Long productId, String batchNumber);

    // Check if batch number exists for product
    boolean existsByProductIdAndBatchNumber(Long productId, String batchNumber);

    // Find batches expiring within days
    @Query("SELECT b FROM ProductBatch b WHERE b.status = 'ACTIVE' " +
            "AND b.expiryDate <= :expiryDate AND b.quantity > 0")
    List<ProductBatch> findExpiringBatches(@Param("expiryDate") LocalDate expiryDate);

    // Find expired batches that are still active
    @Query("SELECT b FROM ProductBatch b WHERE b.status = 'ACTIVE' " +
            "AND b.expiryDate < :currentDate")
    List<ProductBatch> findExpiredActiveBatches(@Param("currentDate") LocalDate currentDate);

    // Get total quantity for a product (active batches only)
    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM ProductBatch b " +
            "WHERE b.product.id = :productId AND b.status IN ('ACTIVE', 'QUARANTINED')")
    Integer getTotalQuantityByProductId(@Param("productId") Long productId);

    // Count batches by status
    Long countByStatus(BatchStatus status);

    // Find batches for expiry monitoring
    @Query("SELECT b FROM ProductBatch b WHERE b.status = 'ACTIVE' " +
            "AND b.expiryDate BETWEEN :startDate AND :endDate " +
            "ORDER BY b.expiryDate ASC")
    List<ProductBatch> findBatchesExpiringBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}