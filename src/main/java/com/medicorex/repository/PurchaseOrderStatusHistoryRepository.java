package com.medicorex.repository;

import com.medicorex.entity.PurchaseOrder.POStatus;
import com.medicorex.entity.PurchaseOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PurchaseOrderStatusHistoryRepository extends JpaRepository<PurchaseOrderStatusHistory, Long> {

    // Find all status changes for a specific PO, ordered by time (most recent first)
    List<PurchaseOrderStatusHistory> findByPoIdOrderByChangedAtDesc(Long poId);

    // Find all status changes for a specific PO number
    List<PurchaseOrderStatusHistory> findByPoNumberOrderByChangedAtDesc(String poNumber);

    // Find status changes by specific status
    List<PurchaseOrderStatusHistory> findByNewStatusOrderByChangedAtDesc(POStatus status);

    // Find recent status changes within time range
    @Query("SELECT sh FROM PurchaseOrderStatusHistory sh " +
            "WHERE sh.changedAt BETWEEN :startDate AND :endDate " +
            "ORDER BY sh.changedAt DESC")
    List<PurchaseOrderStatusHistory> findStatusChangesInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Count status changes for a PO
    Long countByPoId(Long poId);

    // Find most recent status change for a PO
    @Query("SELECT sh FROM PurchaseOrderStatusHistory sh " +
            "WHERE sh.poId = :poId " +
            "ORDER BY sh.changedAt DESC")
    List<PurchaseOrderStatusHistory> findMostRecentByPoId(@Param("poId") Long poId);
}