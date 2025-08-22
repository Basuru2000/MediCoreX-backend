package com.medicorex.repository;

import com.medicorex.entity.ExpiryTrendSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpiryTrendSnapshotRepository extends JpaRepository<ExpiryTrendSnapshot, Long> {

    Optional<ExpiryTrendSnapshot> findBySnapshotDate(LocalDate date);

    List<ExpiryTrendSnapshot> findBySnapshotDateBetweenOrderBySnapshotDate(
            LocalDate startDate, LocalDate endDate);

    @Query("SELECT s FROM ExpiryTrendSnapshot s WHERE s.snapshotDate >= :startDate " +
            "ORDER BY s.snapshotDate DESC")
    List<ExpiryTrendSnapshot> findRecentSnapshots(@Param("startDate") LocalDate startDate);

    @Query("SELECT AVG(s.expiredCount) FROM ExpiryTrendSnapshot s " +
            "WHERE s.snapshotDate BETWEEN :startDate AND :endDate")
    Double getAverageExpiredCount(@Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);

    @Query("SELECT s.criticalCategoryName, COUNT(s) as count " +
            "FROM ExpiryTrendSnapshot s " +
            "WHERE s.snapshotDate >= :startDate " +
            "GROUP BY s.criticalCategoryName " +
            "ORDER BY count DESC")
    List<Object[]> findTopCriticalCategories(@Param("startDate") LocalDate startDate);

    @Query(value = "SELECT " +
            "DATE_FORMAT(snapshot_date, :format) as period, " +
            "AVG(expired_count) as avgExpired, " +
            "AVG(expiring_30_days) as avgExpiring, " +
            "SUM(expired_value) as totalValue " +
            "FROM expiry_trend_snapshots " +
            "WHERE snapshot_date BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE_FORMAT(snapshot_date, :format) " +
            "ORDER BY period", nativeQuery = true)
    List<Object[]> getAggregatedTrends(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("format") String format);

    // Get latest snapshot
    Optional<ExpiryTrendSnapshot> findTopByOrderBySnapshotDateDesc();

    // Check if snapshot exists for today
    boolean existsBySnapshotDate(LocalDate date);
}