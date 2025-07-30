package com.medicorex.repository;

import com.medicorex.entity.ExpiryCheckLog;
import com.medicorex.entity.ExpiryCheckLog.CheckStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@Repository
public interface ExpiryCheckLogRepository extends JpaRepository<ExpiryCheckLog, Long> {

    Optional<ExpiryCheckLog> findByCheckDate(LocalDate checkDate);

    List<ExpiryCheckLog> findTop30ByOrderByCheckDateDesc();

    @Query("SELECT e FROM ExpiryCheckLog e WHERE e.status = 'RUNNING' ORDER BY e.startTime DESC")
    List<ExpiryCheckLog> findRunningChecks();

    @Query("SELECT COUNT(e) > 0 FROM ExpiryCheckLog e WHERE e.checkDate = :date AND e.status = 'COMPLETED'")
    boolean hasCompletedCheckForDate(LocalDate date);

    // New methods for enhanced functionality

    /**
     * Count checks by date and status
     */
    Long countByCheckDateAndStatus(LocalDate checkDate, CheckStatus status);

    /**
     * Find all checks for a specific date ordered by start time
     */
    List<ExpiryCheckLog> findAllByCheckDateOrderByStartTimeDesc(LocalDate checkDate);

    /**
     * Find checks by created by (trigger type)
     */
    @Query("SELECT e FROM ExpiryCheckLog e WHERE e.checkDate = :date AND e.createdBy = :createdBy")
    List<ExpiryCheckLog> findByCheckDateAndCreatedBy(@Param("date") LocalDate date, @Param("createdBy") String createdBy);

    /**
     * Count total completed checks
     */
    @Query("SELECT COUNT(e) FROM ExpiryCheckLog e WHERE e.status = 'COMPLETED'")
    Long countTotalCompletedChecks();

    /**
     * Get average execution time for completed checks
     */
    @Query("SELECT AVG(e.executionTimeMs) FROM ExpiryCheckLog e WHERE e.status = 'COMPLETED' AND e.executionTimeMs IS NOT NULL")
    Double getAverageExecutionTime();

    /**
     * Find checks within date range
     */
    @Query("SELECT e FROM ExpiryCheckLog e WHERE e.checkDate BETWEEN :startDate AND :endDate ORDER BY e.checkDate DESC")
    List<ExpiryCheckLog> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}