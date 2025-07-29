package com.medicorex.repository;

import com.medicorex.entity.ExpiryCheckLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}