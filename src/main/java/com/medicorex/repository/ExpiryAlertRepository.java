package com.medicorex.repository;

import com.medicorex.entity.ExpiryAlert;
import com.medicorex.entity.ExpiryAlertConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpiryAlertRepository extends JpaRepository<ExpiryAlert, Long> {

    Page<ExpiryAlert> findByStatus(ExpiryAlert.AlertStatus status, Pageable pageable);

    List<ExpiryAlert> findByProductIdAndStatus(Long productId, ExpiryAlert.AlertStatus status);

    List<ExpiryAlert> findByAlertDateAndStatus(LocalDate alertDate, ExpiryAlert.AlertStatus status);

    @Query("SELECT ea FROM ExpiryAlert ea WHERE ea.product.id = :productId " +
            "AND ea.config.id = :configId AND ea.status = 'PENDING'")
    List<ExpiryAlert> findPendingAlerts(@Param("productId") Long productId,
                                        @Param("configId") Long configId);

    @Query("SELECT COUNT(ea) FROM ExpiryAlert ea WHERE ea.status = :status")
    Long countByStatus(@Param("status") ExpiryAlert.AlertStatus status);

    @Query("SELECT COUNT(ea) FROM ExpiryAlert ea WHERE ea.status = :status " +
            "AND ea.config.severity = :severity")
    Long countByStatusAndSeverity(@Param("status") ExpiryAlert.AlertStatus status,
                                  @Param("severity") ExpiryAlertConfig.AlertSeverity severity);

    boolean existsByBatchIdAndConfigId(Long batchId, Long configId);

    // ADDED METHODS to fix compilation errors:

    // Check if alert exists for a batch
    boolean existsByBatchId(Long batchId);

    // Find alerts by batch ID
    List<ExpiryAlert> findByBatchId(Long batchId);

    // Alternative: Check with status
    boolean existsByBatchIdAndStatus(Long batchId, ExpiryAlert.AlertStatus status);

    // Get pending alerts for a batch
    @Query("SELECT a FROM ExpiryAlert a WHERE a.batch.id = :batchId AND a.status = 'PENDING'")
    List<ExpiryAlert> findPendingAlertsByBatchId(@Param("batchId") Long batchId);

    // Add this method to existing repository
    @Query("SELECT a FROM ExpiryAlert a WHERE a.expiryDate BETWEEN :startDate AND :endDate " +
            "ORDER BY a.expiryDate, a.config.severity")
    List<ExpiryAlert> findByExpiryDateBetween(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}