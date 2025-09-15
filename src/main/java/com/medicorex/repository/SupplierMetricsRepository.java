package com.medicorex.repository;

import com.medicorex.entity.SupplierMetrics;
import com.medicorex.entity.SupplierMetrics.PerformanceTrend;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierMetricsRepository extends JpaRepository<SupplierMetrics, Long> {

    Optional<SupplierMetrics> findBySupplierIdAndMetricMonth(Long supplierId, LocalDate metricMonth);

    List<SupplierMetrics> findBySupplierIdOrderByMetricMonthDesc(Long supplierId);

    @Query("SELECT sm FROM SupplierMetrics sm WHERE sm.supplier.id = :supplierId " +
            "AND sm.metricMonth BETWEEN :startDate AND :endDate ORDER BY sm.metricMonth DESC")
    List<SupplierMetrics> findBySupplierIdAndDateRange(
            @Param("supplierId") Long supplierId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT sm FROM SupplierMetrics sm WHERE sm.metricMonth = :month " +
            "ORDER BY sm.overallScore DESC")
    List<SupplierMetrics> findTopPerformersForMonth(@Param("month") LocalDate month, Pageable pageable);

    @Query("SELECT sm FROM SupplierMetrics sm WHERE sm.supplier.id = :supplierId " +
            "ORDER BY sm.metricMonth DESC")
    Page<SupplierMetrics> findBySupplierIdWithPagination(
            @Param("supplierId") Long supplierId,
            Pageable pageable
    );

    @Query("SELECT AVG(sm.overallScore) FROM SupplierMetrics sm " +
            "WHERE sm.supplier.id = :supplierId AND sm.metricMonth >= :fromDate")
    BigDecimal calculateAverageScore(
            @Param("supplierId") Long supplierId,
            @Param("fromDate") LocalDate fromDate
    );

    @Query("SELECT sm.performanceTrend, COUNT(sm) FROM SupplierMetrics sm " +
            "WHERE sm.metricMonth = :month GROUP BY sm.performanceTrend")
    List<Object[]> getPerformanceTrendDistribution(@Param("month") LocalDate month);

    @Query("SELECT sm FROM SupplierMetrics sm WHERE sm.overallScore < :threshold " +
            "AND sm.metricMonth = :month")
    List<SupplierMetrics> findUnderperformingSuppliers(
            @Param("threshold") BigDecimal threshold,
            @Param("month") LocalDate month
    );

    @Query("SELECT DISTINCT sm.metricMonth FROM SupplierMetrics sm " +
            "ORDER BY sm.metricMonth DESC")
    List<LocalDate> findAvailableMetricMonths();

    @Query("SELECT COUNT(DISTINCT sm.supplier.id) FROM SupplierMetrics sm " +
            "WHERE sm.metricMonth = :month")
    Long countSuppliersWithMetrics(@Param("month") LocalDate month);
}