package com.medicorex.repository;

import com.medicorex.entity.PurchaseOrder;
import com.medicorex.entity.PurchaseOrder.POStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("SELECT po FROM PurchaseOrder po " +
            "LEFT JOIN FETCH po.supplier " +
            "LEFT JOIN FETCH po.createdBy " +
            "WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT po FROM PurchaseOrder po " +
            "LEFT JOIN FETCH po.supplier s " +
            "LEFT JOIN FETCH po.createdBy " +
            "WHERE po.poNumber = :poNumber")
    Optional<PurchaseOrder> findByPoNumber(@Param("poNumber") String poNumber);

    Page<PurchaseOrder> findByStatus(POStatus status, Pageable pageable);

    Page<PurchaseOrder> findBySupplierId(Long supplierId, Pageable pageable);

    @Query("SELECT po FROM PurchaseOrder po " +
            "WHERE (:status IS NULL OR po.status = :status) " +
            "AND (:supplierId IS NULL OR po.supplier.id = :supplierId) " +
            "AND (:search IS NULL OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(po.supplier.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PurchaseOrder> searchPurchaseOrders(
            @Param("status") POStatus status,
            @Param("supplierId") Long supplierId,
            @Param("search") String search,
            Pageable pageable
    );

    List<PurchaseOrder> findByOrderDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    Long countByStatus(POStatus status);

    boolean existsByPoNumber(String poNumber);

    @Query("SELECT MAX(CAST(SUBSTRING(po.poNumber, 4) AS int)) FROM PurchaseOrder po " +
            "WHERE po.poNumber LIKE :prefix")
    Integer findMaxPoNumberSequence(@Param("prefix") String prefix);

    /**
     * Find all partially received purchase orders
     */
    @Query("SELECT po FROM PurchaseOrder po WHERE po.status = 'PARTIALLY_RECEIVED'")
    Page<PurchaseOrder> findPartiallyReceived(Pageable pageable);

    /**
     * Find POs with remaining quantities
     */
    @Query("SELECT DISTINCT po FROM PurchaseOrder po " +
            "JOIN po.lines line " +
            "WHERE line.remainingQuantity > 0 " +
            "AND po.status IN ('SENT', 'PARTIALLY_RECEIVED')")
    List<PurchaseOrder> findPurchaseOrdersWithRemainingItems();

    @Query("SELECT po FROM PurchaseOrder po " +
            "WHERE po.status IN ('SENT', 'PARTIALLY_RECEIVED') " +
            "ORDER BY po.orderDate DESC")
    List<PurchaseOrder> findEligibleForReceiving();
}