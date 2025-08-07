package com.medicorex.repository;

import com.medicorex.entity.QuarantineRecord;
import com.medicorex.entity.QuarantineRecord.QuarantineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface QuarantineRecordRepository extends JpaRepository<QuarantineRecord, Long> {

    // Find by status
    Page<QuarantineRecord> findByStatus(QuarantineStatus status, Pageable pageable);

    // Find by multiple statuses
    Page<QuarantineRecord> findByStatusIn(List<QuarantineStatus> statuses, Pageable pageable);

    // Find by batch
    List<QuarantineRecord> findByBatchId(Long batchId);

    // Find by product
    List<QuarantineRecord> findByProductId(Long productId);

    // Find pending review items
    @Query("SELECT q FROM QuarantineRecord q WHERE q.status = 'PENDING_REVIEW' " +
            "ORDER BY q.quarantineDate ASC")
    List<QuarantineRecord> findPendingReview();

    // Count by status
    Long countByStatus(QuarantineStatus status);

    // Get total quarantined quantity
    @Query("SELECT COALESCE(SUM(q.quantityQuarantined), 0) FROM QuarantineRecord q " +
            "WHERE q.status NOT IN ('DISPOSED', 'RETURNED')")
    Integer getTotalQuarantinedQuantity();

    // Get total estimated loss
    @Query("SELECT COALESCE(SUM(q.estimatedLoss), 0) FROM QuarantineRecord q " +
            "WHERE q.status NOT IN ('RETURNED')")
    BigDecimal getTotalEstimatedLoss();

    // Find records quarantined between dates
    @Query("SELECT q FROM QuarantineRecord q WHERE q.quarantineDate BETWEEN :startDate AND :endDate")
    List<QuarantineRecord> findByQuarantineDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Get summary statistics
    @Query("SELECT new map(" +
            "COUNT(q) as totalRecords, " +
            "SUM(q.quantityQuarantined) as totalQuantity, " +
            "SUM(q.estimatedLoss) as totalLoss, " +
            "SUM(CASE WHEN q.status = 'PENDING_REVIEW' THEN 1 ELSE 0 END) as pendingCount) " +
            "FROM QuarantineRecord q")
    Map<String, Object> getQuarantineSummary();
}