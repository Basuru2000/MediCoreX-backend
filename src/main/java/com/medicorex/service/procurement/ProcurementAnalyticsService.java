package com.medicorex.service.procurement;

import com.medicorex.dto.*;
import com.medicorex.entity.PurchaseOrder.POStatus;
import com.medicorex.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcurementAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get overall procurement metrics summary
     */
    public ProcurementMetricsDTO getProcurementMetrics(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching procurement metrics from {} to {}", startDate, endDate);

        String sql = """
            SELECT 
                COUNT(*) as total_pos,
                SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END) as pending_approval,
                SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) as approved,
                SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) as sent,
                SUM(CASE WHEN status = 'PARTIALLY_RECEIVED' THEN 1 ELSE 0 END) as partially_received,
                SUM(CASE WHEN status = 'RECEIVED' THEN 1 ELSE 0 END) as received,
                SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled,
                SUM(total_amount) as total_value,
                AVG(total_amount) as avg_po_value,
                SUM(CASE WHEN status IN ('DRAFT', 'APPROVED', 'SENT') THEN total_amount ELSE 0 END) as pending_value,
                AVG(TIMESTAMPDIFF(HOUR, created_at, approved_date)) as avg_approval_time
            FROM purchase_orders
            WHERE order_date BETWEEN ? AND ?
            """;

        ProcurementMetricsDTO metrics = jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> ProcurementMetricsDTO.builder()
                        .totalPOs(rs.getLong("total_pos"))
                        .pendingApproval(rs.getLong("pending_approval"))
                        .approved(rs.getLong("approved"))
                        .sent(rs.getLong("sent"))
                        .partiallyReceived(rs.getLong("partially_received"))
                        .received(rs.getLong("received"))
                        .cancelled(rs.getLong("cancelled"))
                        .totalValue(rs.getBigDecimal("total_value") != null ? rs.getBigDecimal("total_value") : BigDecimal.ZERO)
                        .avgPOValue(rs.getBigDecimal("avg_po_value") != null ? rs.getBigDecimal("avg_po_value") : BigDecimal.ZERO)
                        .pendingValue(rs.getBigDecimal("pending_value") != null ? rs.getBigDecimal("pending_value") : BigDecimal.ZERO)
                        .avgApprovalTimeHours(rs.getDouble("avg_approval_time"))
                        .build(),
                startDate, endDate
        );

        // Calculate fulfillment rate
        metrics.setFulfillmentRate(calculateFulfillmentRate(startDate, endDate));

        // Calculate monthly growth
        calculateMonthlyGrowth(metrics, startDate, endDate);

        return metrics;
    }

    /**
     * Get PO trends over time (monthly aggregation)
     */
    public List<POTrendDataDTO> getPOTrends(Integer months) {
        log.info("Fetching PO trends for last {} months", months);

        String sql = """
            SELECT * FROM po_monthly_trends
            ORDER BY month DESC
            LIMIT ?
            """;

        List<POTrendDataDTO> trends = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    String month = rs.getString("month");
                    return POTrendDataDTO.builder()
                            .month(month)
                            .monthLabel(formatMonthLabel(month))
                            .poCount(rs.getLong("po_count"))
                            .totalValue(rs.getBigDecimal("total_value") != null ? rs.getBigDecimal("total_value") : BigDecimal.ZERO)
                            .avgPoValue(rs.getBigDecimal("avg_po_value") != null ? rs.getBigDecimal("avg_po_value") : BigDecimal.ZERO)
                            .approvedCount(rs.getLong("approved_count"))
                            .receivedCount(rs.getLong("received_count"))
                            .cancelledCount(rs.getLong("cancelled_count"))
                            .avgApprovalTimeHours(rs.getDouble("avg_approval_time_hours"))
                            .build();
                },
                months
        );

        // Return in chronological order (oldest first for charts)
        return trends.stream()
                .sorted((a, b) -> a.getMonth().compareTo(b.getMonth()))
                .collect(Collectors.toList());
    }

    /**
     * Get top suppliers by PO volume or value
     */
    public List<TopSupplierDTO> getTopSuppliers(Integer limit, String sortBy) {
        log.info("Fetching top {} suppliers sorted by {}", limit, sortBy);

        String orderByClause = sortBy.equals("value") ? "total_value DESC" : "total_pos DESC";

        String sql = String.format("""
            SELECT * FROM supplier_performance_analytics
            WHERE total_pos > 0
            ORDER BY %s
            LIMIT ?
            """, orderByClause);

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> TopSupplierDTO.builder()
                        .supplierId(rs.getLong("supplier_id"))
                        .supplierName(rs.getString("supplier_name"))
                        .supplierCode(rs.getString("supplier_code"))
                        .totalPOs(rs.getLong("total_pos"))
                        .totalValue(rs.getBigDecimal("total_value") != null ? rs.getBigDecimal("total_value") : BigDecimal.ZERO)
                        .avgPOValue(rs.getBigDecimal("avg_po_value") != null ? rs.getBigDecimal("avg_po_value") : BigDecimal.ZERO)
                        .completedPOs(rs.getLong("completed_pos"))
                        .cancelledPOs(rs.getLong("cancelled_pos"))
                        .completionRate(rs.getDouble("completion_rate"))
                        .avgApprovalTimeHours(rs.getDouble("avg_approval_time_hours"))
                        .lastOrderDate(rs.getDate("last_order_date") != null ? rs.getDate("last_order_date").toLocalDate() : null)
                        .build(),
                limit
        );
    }

    /**
     * Get PO status distribution
     */
    public List<POStatusDistributionDTO> getStatusDistribution(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching status distribution from {} to {}", startDate, endDate);

        String sql = """
            SELECT 
                status,
                COUNT(*) as count,
                SUM(total_amount) as total_value,
                AVG(total_amount) as avg_value
            FROM purchase_orders
            WHERE order_date BETWEEN ? AND ?
            GROUP BY status
            """;

        List<POStatusDistributionDTO> distribution = jdbcTemplate.query(sql,
                (rs, rowNum) -> POStatusDistributionDTO.builder()
                        .status(rs.getString("status"))
                        .count(rs.getLong("count"))
                        .totalValue(rs.getBigDecimal("total_value") != null ? rs.getBigDecimal("total_value") : BigDecimal.ZERO)
                        .avgValue(rs.getBigDecimal("avg_value") != null ? rs.getBigDecimal("avg_value") : BigDecimal.ZERO)
                        .build(),
                startDate, endDate
        );

        // Calculate percentages
        long totalCount = distribution.stream().mapToLong(POStatusDistributionDTO::getCount).sum();
        distribution.forEach(d -> {
            double percentage = totalCount > 0 ? (d.getCount() * 100.0 / totalCount) : 0;
            d.setPercentage(Math.round(percentage * 100.0) / 100.0);
        });

        return distribution;
    }

    /**
     * Calculate fulfillment rate
     */
    private Double calculateFulfillmentRate(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                SUM(pol.quantity) as total_ordered,
                SUM(pol.received_quantity) as total_received
            FROM purchase_order_lines pol
            INNER JOIN purchase_orders po ON pol.po_id = po.id
            WHERE po.order_date BETWEEN ? AND ?
            """;

        return jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> {
                    long totalOrdered = rs.getLong("total_ordered");
                    long totalReceived = rs.getLong("total_received");
                    if (totalOrdered == 0) return 0.0;
                    return Math.round((totalReceived * 100.0 / totalOrdered) * 100.0) / 100.0;
                },
                startDate, endDate
        );
    }

    /**
     * Calculate monthly growth metrics
     */
    private void calculateMonthlyGrowth(ProcurementMetricsDTO metrics, LocalDate startDate, LocalDate endDate) {
        // Get this month's count
        LocalDate thisMonthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate thisMonthEnd = LocalDate.now();

        String sqlThisMonth = "SELECT COUNT(*) FROM purchase_orders WHERE order_date BETWEEN ? AND ?";
        Integer thisMonth = jdbcTemplate.queryForObject(sqlThisMonth, Integer.class, thisMonthStart, thisMonthEnd);
        metrics.setPosThisMonth(thisMonth != null ? thisMonth : 0);

        // Get last month's count
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        LocalDate lastMonthEnd = thisMonthStart.minusDays(1);

        Integer lastMonth = jdbcTemplate.queryForObject(sqlThisMonth, Integer.class, lastMonthStart, lastMonthEnd);
        metrics.setPosLastMonth(lastMonth != null ? lastMonth : 0);

        // Calculate growth percentage
        if (lastMonth != null && lastMonth > 0) {
            double growth = ((thisMonth - lastMonth) * 100.0) / lastMonth;
            metrics.setMonthlyGrowthPercentage(Math.round(growth * 100.0) / 100.0);
        } else {
            metrics.setMonthlyGrowthPercentage(0.0);
        }
    }

    /**
     * Format month label for display
     */
    private String formatMonthLabel(String month) {
        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("MMM yyyy");
            LocalDate date = LocalDate.parse(month + "-01");
            return date.format(outputFormatter);
        } catch (Exception e) {
            return month;
        }
    }
}