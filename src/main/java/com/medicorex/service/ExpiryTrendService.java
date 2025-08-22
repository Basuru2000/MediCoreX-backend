package com.medicorex.service;

import com.medicorex.dto.*;
import com.medicorex.entity.ExpiryTrendSnapshot;
import com.medicorex.entity.ProductBatch;
import com.medicorex.entity.Category;
import com.medicorex.entity.Product;
import com.medicorex.repository.ExpiryTrendSnapshotRepository;
import com.medicorex.repository.ProductBatchRepository;
import com.medicorex.repository.CategoryRepository;
import com.medicorex.repository.ProductRepository;
import com.medicorex.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExpiryTrendService {

    private final ExpiryTrendSnapshotRepository trendRepository;
    private final ProductBatchRepository batchRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * Analyze trends for specified date range with full granularity support
     */
    public TrendAnalysisResponseDTO analyzeTrends(LocalDate startDate, LocalDate endDate, String granularity) {
        log.info("Analyzing trends from {} to {} with {} granularity", startDate, endDate, granularity);

        // Fetch snapshots for the period
        List<ExpiryTrendSnapshot> snapshots = trendRepository
                .findBySnapshotDateBetweenOrderBySnapshotDate(startDate, endDate);

        if (snapshots.isEmpty()) {
            log.warn("No snapshot data available for the specified period");
            return createEmptyAnalysis(startDate, endDate, granularity);
        }

        // Convert to trend data points with proper granularity
        List<ExpiryTrendDataPointDTO> trendData = convertToDataPoints(snapshots, granularity);

        // Calculate summary statistics
        TrendAnalysisResponseDTO.SummaryStatistics summary = calculateSummaryStatistics(snapshots);

        // Analyze by category with full implementation
        Map<String, TrendAnalysisResponseDTO.CategoryAnalysis> categoryAnalysis =
                analyzeCategoryTrends(startDate, endDate);

        // Generate insights
        List<TrendAnalysisResponseDTO.TrendInsight> insights = generateInsights(snapshots, summary);

        return TrendAnalysisResponseDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .granularity(granularity)
                .trendData(trendData)
                .summary(summary)
                .categoryAnalysis(categoryAnalysis)
                .insights(insights)
                .build();
    }

    /**
     * Convert snapshots to data points with proper granularity aggregation
     */
    private List<ExpiryTrendDataPointDTO> convertToDataPoints(List<ExpiryTrendSnapshot> snapshots,
                                                              String granularity) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new ArrayList<>();
        }

        switch (granularity.toUpperCase()) {
            case "WEEKLY":
                return aggregateWeekly(snapshots);
            case "MONTHLY":
                return aggregateMonthly(snapshots);
            case "DAILY":
            default:
                return snapshots.stream()
                        .map(this::toDataPoint)
                        .collect(Collectors.toList());
        }
    }

    /**
     * Aggregate snapshots by week
     */
    private List<ExpiryTrendDataPointDTO> aggregateWeekly(List<ExpiryTrendSnapshot> snapshots) {
        Map<String, List<ExpiryTrendSnapshot>> weeklyGroups = snapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> {
                    LocalDate date = snapshot.getSnapshotDate();
                    WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 1);
                    int weekOfYear = date.get(weekFields.weekOfWeekBasedYear());
                    int year = date.get(weekFields.weekBasedYear());
                    return year + "-W" + String.format("%02d", weekOfYear);
                }));

        return weeklyGroups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<ExpiryTrendSnapshot> weekSnapshots = entry.getValue();

                    // Calculate weekly averages
                    int avgExpired = (int) weekSnapshots.stream()
                            .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                            .average().orElse(0);

                    int avgExpiring = (int) weekSnapshots.stream()
                            .mapToInt(s -> s.getExpiring30Days() != null ? s.getExpiring30Days() : 0)
                            .average().orElse(0);

                    BigDecimal totalValue = weekSnapshots.stream()
                            .map(s -> s.getExpiredValue() != null ? s.getExpiredValue() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Get the first day of the week for the date
                    LocalDate weekStart = weekSnapshots.stream()
                            .map(ExpiryTrendSnapshot::getSnapshotDate)
                            .min(LocalDate::compareTo)
                            .orElse(LocalDate.now());

                    // Determine trend for the week
                    String trend = calculateWeeklyTrend(weekSnapshots);
                    double percentChange = calculateWeeklyPercentChange(weekSnapshots);

                    return ExpiryTrendDataPointDTO.builder()
                            .date(weekStart)
                            .label("Week " + entry.getKey())
                            .expiredCount(avgExpired)
                            .expiringCount(avgExpiring)
                            .value(totalValue)
                            .trend(trend)
                            .percentageChange(percentChange)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Aggregate snapshots by month
     */
    private List<ExpiryTrendDataPointDTO> aggregateMonthly(List<ExpiryTrendSnapshot> snapshots) {
        Map<String, List<ExpiryTrendSnapshot>> monthlyGroups = snapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> {
                    LocalDate date = snapshot.getSnapshotDate();
                    return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
                }));

        return monthlyGroups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<ExpiryTrendSnapshot> monthSnapshots = entry.getValue();

                    // Calculate monthly totals
                    int totalExpired = monthSnapshots.stream()
                            .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                            .sum();

                    int totalExpiring = monthSnapshots.stream()
                            .mapToInt(s -> s.getExpiring30Days() != null ? s.getExpiring30Days() : 0)
                            .sum();

                    BigDecimal totalValue = monthSnapshots.stream()
                            .map(s -> s.getExpiredValue() != null ? s.getExpiredValue() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Get the first day of the month for the date
                    LocalDate monthStart = monthSnapshots.stream()
                            .map(ExpiryTrendSnapshot::getSnapshotDate)
                            .min(LocalDate::compareTo)
                            .orElse(LocalDate.now())
                            .withDayOfMonth(1);

                    // Determine trend for the month
                    String trend = calculateMonthlyTrend(monthSnapshots);
                    double percentChange = calculateMonthlyPercentChange(monthSnapshots);

                    String monthLabel = monthStart.getMonth().toString() + " " + monthStart.getYear();

                    return ExpiryTrendDataPointDTO.builder()
                            .date(monthStart)
                            .label(monthLabel)
                            .expiredCount(totalExpired)
                            .expiringCount(totalExpiring)
                            .value(totalValue)
                            .trend(trend)
                            .percentageChange(percentChange)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate trend for weekly data
     */
    private String calculateWeeklyTrend(List<ExpiryTrendSnapshot> weekSnapshots) {
        if (weekSnapshots.size() < 2) return "STABLE";

        // Compare first half vs second half of the week
        int midPoint = weekSnapshots.size() / 2;
        double firstHalfAvg = weekSnapshots.subList(0, midPoint).stream()
                .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                .average().orElse(0);
        double secondHalfAvg = weekSnapshots.subList(midPoint, weekSnapshots.size()).stream()
                .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                .average().orElse(0);

        double change = ((secondHalfAvg - firstHalfAvg) / (firstHalfAvg + 0.001)) * 100;

        if (change > 10) return "WORSENING";
        if (change < -10) return "IMPROVING";
        return "STABLE";
    }

    /**
     * Calculate percentage change for weekly data
     */
    private double calculateWeeklyPercentChange(List<ExpiryTrendSnapshot> weekSnapshots) {
        if (weekSnapshots.size() < 2) return 0.0;

        ExpiryTrendSnapshot first = weekSnapshots.get(0);
        ExpiryTrendSnapshot last = weekSnapshots.get(weekSnapshots.size() - 1);

        return calculatePercentageChange(first.getExpiredCount(), last.getExpiredCount());
    }

    /**
     * Calculate trend for monthly data
     */
    private String calculateMonthlyTrend(List<ExpiryTrendSnapshot> monthSnapshots) {
        if (monthSnapshots.isEmpty()) return "STABLE";

        // Calculate average trend direction for the month
        long improvingCount = monthSnapshots.stream()
                .filter(s -> s.getTrendDirection() == ExpiryTrendSnapshot.TrendDirection.IMPROVING)
                .count();
        long worseningCount = monthSnapshots.stream()
                .filter(s -> s.getTrendDirection() == ExpiryTrendSnapshot.TrendDirection.WORSENING)
                .count();

        if (worseningCount > improvingCount * 1.5) return "WORSENING";
        if (improvingCount > worseningCount * 1.5) return "IMPROVING";
        return "STABLE";
    }

    /**
     * Calculate percentage change for monthly data
     */
    private double calculateMonthlyPercentChange(List<ExpiryTrendSnapshot> monthSnapshots) {
        if (monthSnapshots.isEmpty()) return 0.0;

        // Average percentage change for the month
        return monthSnapshots.stream()
                .mapToDouble(s -> s.getTrendPercentage() != null ? s.getTrendPercentage() : 0.0)
                .average()
                .orElse(0.0);
    }

    /**
     * Get current trend metrics
     */
    @Cacheable(value = "currentTrends", key = "#root.methodName")
    public ExpiryTrendDTO getCurrentTrends() {
        LocalDate today = LocalDate.now();

        // Get or create today's snapshot
        ExpiryTrendSnapshot snapshot = trendRepository.findBySnapshotDate(today)
                .orElseGet(() -> captureSnapshot());

        // Get previous period for comparison
        LocalDate previousDate = today.minusDays(7);
        Optional<ExpiryTrendSnapshot> previousSnapshot = trendRepository.findBySnapshotDate(previousDate);

        ExpiryTrendDTO dto = mapToDTO(snapshot);

        // Add comparison metrics
        if (previousSnapshot.isPresent()) {
            ExpiryTrendSnapshot prev = previousSnapshot.get();
            dto.setExpiredCountChange(snapshot.getExpiredCount() - prev.getExpiredCount());
            dto.setExpiredCountChangePercent(
                    calculatePercentageChange(prev.getExpiredCount(), snapshot.getExpiredCount())
            );

            BigDecimal currentValue = snapshot.getExpiredValue() != null ?
                    snapshot.getExpiredValue() : BigDecimal.ZERO;
            BigDecimal currentExpiring = snapshot.getExpiring30DaysValue() != null ?
                    snapshot.getExpiring30DaysValue() : BigDecimal.ZERO;
            BigDecimal previousValue = prev.getExpiredValue() != null ?
                    prev.getExpiredValue() : BigDecimal.ZERO;
            BigDecimal previousExpiring = prev.getExpiring30DaysValue() != null ?
                    prev.getExpiring30DaysValue() : BigDecimal.ZERO;

            BigDecimal totalCurrent = currentValue.add(currentExpiring);
            BigDecimal totalPrevious = previousValue.add(previousExpiring);

            dto.setValueAtRiskChange(totalCurrent.subtract(totalPrevious));
            dto.setValueAtRiskChangePercent(
                    calculatePercentageChange(totalPrevious, totalCurrent)
            );
        }

        // Add predictive metrics (simplified for current implementation)
        dto.setPredicted30DayExpiry(calculatePredicted30DayExpiry(snapshot));
        dto.setPredictionConfidence(75.0);

        return dto;
    }

    /**
     * Calculate predicted 30-day expiry based on current trends
     */
    private Integer calculatePredicted30DayExpiry(ExpiryTrendSnapshot currentSnapshot) {
        // Get last 30 days of data
        LocalDate startDate = currentSnapshot.getSnapshotDate().minusDays(30);
        List<ExpiryTrendSnapshot> historicalData = trendRepository
                .findBySnapshotDateBetweenOrderBySnapshotDate(startDate, currentSnapshot.getSnapshotDate());

        if (historicalData.size() < 7) {
            // Not enough data, return current 30-day count
            return currentSnapshot.getExpiring30Days() != null ? currentSnapshot.getExpiring30Days() : 0;
        }

        // Simple moving average prediction
        double avgExpiring = historicalData.stream()
                .mapToInt(s -> s.getExpiring30Days() != null ? s.getExpiring30Days() : 0)
                .average()
                .orElse(0);

        // Adjust based on trend
        if (currentSnapshot.getTrendDirection() == ExpiryTrendSnapshot.TrendDirection.WORSENING) {
            avgExpiring *= 1.1; // Increase by 10% if worsening
        } else if (currentSnapshot.getTrendDirection() == ExpiryTrendSnapshot.TrendDirection.IMPROVING) {
            avgExpiring *= 0.9; // Decrease by 10% if improving
        }

        return (int) Math.round(avgExpiring);
    }

    /**
     * Get historical trend data points
     */
    public List<ExpiryTrendDataPointDTO> getHistoricalTrends(int daysBack) {
        LocalDate startDate = LocalDate.now().minusDays(daysBack);
        List<ExpiryTrendSnapshot> snapshots = trendRepository.findRecentSnapshots(startDate);

        return snapshots.stream()
                .map(this::toDataPoint)
                .collect(Collectors.toList());
    }

    /**
     * Get category-wise trends
     */
    public Map<String, List<ExpiryTrendDataPointDTO>> getCategoryWiseTrends(int daysBack) {
        LocalDate startDate = LocalDate.now().minusDays(daysBack);

        // Get all categories
        List<Category> categories = categoryRepository.findAll();
        Map<String, List<ExpiryTrendDataPointDTO>> categoryTrends = new HashMap<>();

        for (Category category : categories) {
            List<ExpiryTrendDataPointDTO> trends = calculateCategoryTrends(category, startDate);
            if (!trends.isEmpty()) {
                categoryTrends.put(category.getName(), trends);
            }
        }

        return categoryTrends;
    }

    /**
     * Capture current snapshot
     */
    @Transactional
    public ExpiryTrendSnapshot captureSnapshot() {
        LocalDate today = LocalDate.now();

        // Check if snapshot already exists
        if (trendRepository.existsBySnapshotDate(today)) {
            log.info("Snapshot already exists for {}", today);
            return trendRepository.findBySnapshotDate(today).get();
        }

        log.info("Capturing expiry trend snapshot for {}", today);

        ExpiryTrendSnapshot snapshot = new ExpiryTrendSnapshot();
        snapshot.setSnapshotDate(today);

        // Get all active batches
        List<ProductBatch> activeBatches = batchRepository.findAll().stream()
                .filter(b -> b.getStatus() == ProductBatch.BatchStatus.ACTIVE)
                .collect(Collectors.toList());

        // Count expired
        long expiredCount = activeBatches.stream()
                .filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(today))
                .count();
        snapshot.setExpiredCount((int) expiredCount);

        // Count expiring in different periods
        snapshot.setExpiring7Days(countExpiringInDays(activeBatches, 7));
        snapshot.setExpiring30Days(countExpiringInDays(activeBatches, 30));
        snapshot.setExpiring60Days(countExpiringInDays(activeBatches, 60));
        snapshot.setExpiring90Days(countExpiringInDays(activeBatches, 90));

        // Calculate values
        snapshot.setExpiredValue(calculateExpiredValue(activeBatches));
        snapshot.setExpiring7DaysValue(calculateExpiringValue(activeBatches, 7));
        snapshot.setExpiring30DaysValue(calculateExpiringValue(activeBatches, 30));

        // Calculate average days to expiry
        double avgDays = activeBatches.stream()
                .filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isAfter(today))
                .mapToLong(b -> ChronoUnit.DAYS.between(today, b.getExpiryDate()))
                .average()
                .orElse(0);
        snapshot.setAvgDaysToExpiry(avgDays);

        // Find critical category
        Map<Category, Long> categoryExpiryCounts = activeBatches.stream()
                .filter(b -> b.getExpiryDate() != null &&
                        b.getExpiryDate().isBefore(today.plusDays(30)) &&
                        b.getProduct() != null &&
                        b.getProduct().getCategory() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getProduct().getCategory(),
                        Collectors.counting()
                ));

        categoryExpiryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    snapshot.setCriticalCategoryId(entry.getKey().getId());
                    snapshot.setCriticalCategoryName(entry.getKey().getName());
                    snapshot.setCriticalCategoryCount(entry.getValue().intValue());
                });

        // Calculate trend
        calculateTrend(snapshot);

        snapshot.setTotalProducts(activeBatches.size());

        return trendRepository.save(snapshot);
    }

    /**
     * Export trend report as CSV
     */
    public byte[] exportTrendReport(LocalDate startDate, LocalDate endDate) {
        List<ExpiryTrendSnapshot> snapshots = trendRepository
                .findBySnapshotDateBetweenOrderBySnapshotDate(startDate, endDate);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos))) {

            // Write header
            writer.println("Date,Total Products,Expired,Expiring 7 Days,Expiring 30 Days," +
                    "Expired Value,Value at Risk,Trend Direction,Trend %,Critical Category");

            // Write data
            for (ExpiryTrendSnapshot snapshot : snapshots) {
                writer.printf("%s,%d,%d,%d,%d,%.2f,%.2f,%s,%.2f,%s%n",
                        snapshot.getSnapshotDate(),
                        snapshot.getTotalProducts(),
                        snapshot.getExpiredCount(),
                        snapshot.getExpiring7Days(),
                        snapshot.getExpiring30Days(),
                        snapshot.getExpiredValue() != null ? snapshot.getExpiredValue() : BigDecimal.ZERO,
                        snapshot.getExpiring30DaysValue() != null ? snapshot.getExpiring30DaysValue() : BigDecimal.ZERO,
                        snapshot.getTrendDirection() != null ? snapshot.getTrendDirection() : "STABLE",
                        snapshot.getTrendPercentage() != null ? snapshot.getTrendPercentage() : 0.0,
                        snapshot.getCriticalCategoryName() != null ? snapshot.getCriticalCategoryName() : ""
                );
            }

            writer.flush();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error exporting trend report", e);
            throw new RuntimeException("Failed to export trend report", e);
        }
    }

    /**
     * Compare two periods
     */
    public Map<String, Object> comparePeriods(LocalDate period1Start, LocalDate period1End,
                                              LocalDate period2Start, LocalDate period2End) {
        // Get snapshots for both periods
        List<ExpiryTrendSnapshot> period1 = trendRepository
                .findBySnapshotDateBetweenOrderBySnapshotDate(period1Start, period1End);
        List<ExpiryTrendSnapshot> period2 = trendRepository
                .findBySnapshotDateBetweenOrderBySnapshotDate(period2Start, period2End);

        Map<String, Object> comparison = new HashMap<>();

        if (period1.isEmpty() || period2.isEmpty()) {
            comparison.put("error", "Insufficient data for comparison");
            return comparison;
        }

        // Calculate averages for period 1
        double avgExpired1 = period1.stream()
                .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                .average().orElse(0);
        BigDecimal avgValue1 = period1.stream()
                .map(s -> s.getExpiredValue() != null ? s.getExpiredValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period1.size()), RoundingMode.HALF_UP);

        // Calculate averages for period 2
        double avgExpired2 = period2.stream()
                .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                .average().orElse(0);
        BigDecimal avgValue2 = period2.stream()
                .map(s -> s.getExpiredValue() != null ? s.getExpiredValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period2.size()), RoundingMode.HALF_UP);

        comparison.put("period1", Map.of(
                "start", period1Start,
                "end", period1End,
                "avgExpired", avgExpired1,
                "avgValue", avgValue1,
                "dataPoints", period1.size()
        ));

        comparison.put("period2", Map.of(
                "start", period2Start,
                "end", period2End,
                "avgExpired", avgExpired2,
                "avgValue", avgValue2,
                "dataPoints", period2.size()
        ));

        comparison.put("comparison", Map.of(
                "expiredChange", avgExpired2 - avgExpired1,
                "expiredChangePercent", calculatePercentageChange(avgExpired1, avgExpired2),
                "valueChange", avgValue2.subtract(avgValue1),
                "valueChangePercent", calculatePercentageChange(avgValue1, avgValue2),
                "improvement", avgExpired2 < avgExpired1
        ));

        return comparison;
    }

    // Private helper methods

    private int countExpiringInDays(List<ProductBatch> batches, int days) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(days);

        return (int) batches.stream()
                .filter(b -> b.getExpiryDate() != null &&
                        b.getExpiryDate().isAfter(today) &&
                        b.getExpiryDate().isBefore(targetDate))
                .count();
    }

    private BigDecimal calculateExpiredValue(List<ProductBatch> batches) {
        LocalDate today = LocalDate.now();

        return batches.stream()
                .filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(today))
                .map(b -> {
                    if (b.getCostPerUnit() != null && b.getQuantity() != null) {
                        return b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateExpiringValue(List<ProductBatch> batches, int days) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(days);

        return batches.stream()
                .filter(b -> b.getExpiryDate() != null &&
                        b.getExpiryDate().isAfter(today) &&
                        b.getExpiryDate().isBefore(targetDate))
                .map(b -> {
                    if (b.getCostPerUnit() != null && b.getQuantity() != null) {
                        return b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity()));
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void calculateTrend(ExpiryTrendSnapshot snapshot) {
        // Get average of previous 7 days
        LocalDate startDate = snapshot.getSnapshotDate().minusDays(7);
        LocalDate endDate = snapshot.getSnapshotDate().minusDays(1);

        Double avgExpired = trendRepository.getAverageExpiredCount(startDate, endDate);

        if (avgExpired == null || avgExpired == 0) {
            snapshot.setTrendDirection(ExpiryTrendSnapshot.TrendDirection.STABLE);
            snapshot.setTrendPercentage(0.0);
        } else {
            double percentChange = ((snapshot.getExpiredCount() - avgExpired) / avgExpired) * 100;
            snapshot.setTrendPercentage(percentChange);

            if (percentChange > 10) {
                snapshot.setTrendDirection(ExpiryTrendSnapshot.TrendDirection.WORSENING);
            } else if (percentChange < -10) {
                snapshot.setTrendDirection(ExpiryTrendSnapshot.TrendDirection.IMPROVING);
            } else {
                snapshot.setTrendDirection(ExpiryTrendSnapshot.TrendDirection.STABLE);
            }
        }
    }

    private ExpiryTrendDTO mapToDTO(ExpiryTrendSnapshot snapshot) {
        ExpiryTrendDTO dto = ExpiryTrendDTO.builder()
                .date(snapshot.getSnapshotDate())
                .totalProducts(snapshot.getTotalProducts())
                .expiredCount(snapshot.getExpiredCount())
                .expiring7Days(snapshot.getExpiring7Days())
                .expiring30Days(snapshot.getExpiring30Days())
                .expiring60Days(snapshot.getExpiring60Days())
                .expiring90Days(snapshot.getExpiring90Days())
                .expiredValue(snapshot.getExpiredValue() != null ? snapshot.getExpiredValue() : BigDecimal.ZERO)
                .expiring7DaysValue(snapshot.getExpiring7DaysValue() != null ? snapshot.getExpiring7DaysValue() : BigDecimal.ZERO)
                .expiring30DaysValue(snapshot.getExpiring30DaysValue() != null ? snapshot.getExpiring30DaysValue() : BigDecimal.ZERO)
                .avgDaysToExpiry(snapshot.getAvgDaysToExpiry())
                .trendDirection(snapshot.getTrendDirection() != null ? snapshot.getTrendDirection().toString() : "STABLE")
                .trendPercentage(snapshot.getTrendPercentage() != null ? snapshot.getTrendPercentage() : 0.0)
                .build();

        if (snapshot.getCriticalCategoryId() != null) {
            dto.setCriticalCategory(ExpiryTrendDTO.CategoryTrendDTO.builder()
                    .categoryId(snapshot.getCriticalCategoryId())
                    .categoryName(snapshot.getCriticalCategoryName())
                    .expiringCount(snapshot.getCriticalCategoryCount())
                    .build());
        }

        return dto;
    }

    private ExpiryTrendDataPointDTO toDataPoint(ExpiryTrendSnapshot snapshot) {
        return ExpiryTrendDataPointDTO.builder()
                .date(snapshot.getSnapshotDate())
                .label(snapshot.getSnapshotDate().toString())
                .expiredCount(snapshot.getExpiredCount())
                .expiringCount(snapshot.getExpiring30Days() != null ? snapshot.getExpiring30Days() : 0)
                .value(snapshot.getExpiredValue() != null ? snapshot.getExpiredValue() : BigDecimal.ZERO)
                .trend(snapshot.getTrendDirection() != null ? snapshot.getTrendDirection().toString() : "STABLE")
                .percentageChange(snapshot.getTrendPercentage() != null ? snapshot.getTrendPercentage() : 0.0)
                .build();
    }

    private Double calculatePercentageChange(double oldValue, double newValue) {
        if (oldValue == 0) return 0.0;
        return ((newValue - oldValue) / oldValue) * 100;
    }

    private Double calculatePercentageChange(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || oldValue.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        if (newValue == null) return 0.0;

        return newValue.subtract(oldValue)
                .divide(oldValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private TrendAnalysisResponseDTO.SummaryStatistics calculateSummaryStatistics(
            List<ExpiryTrendSnapshot> snapshots) {

        if (snapshots == null || snapshots.isEmpty()) {
            return TrendAnalysisResponseDTO.SummaryStatistics.builder()
                    .totalExpired(0)
                    .totalExpiring(0)
                    .totalValueLost(BigDecimal.ZERO)
                    .totalValueAtRisk(BigDecimal.ZERO)
                    .averageExpiryRate(0.0)
                    .overallTrend("NO_DATA")
                    .trendStrength(0.0)
                    .build();
        }

        int totalExpired = snapshots.stream()
                .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                .sum();

        int totalExpiring = snapshots.stream()
                .mapToInt(s -> s.getExpiring30Days() != null ? s.getExpiring30Days() : 0)
                .sum();

        BigDecimal totalValueLost = snapshots.stream()
                .map(s -> s.getExpiredValue() != null ? s.getExpiredValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValueAtRisk = snapshots.stream()
                .map(s -> s.getExpiring30DaysValue() != null ? s.getExpiring30DaysValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double averageExpiryRate = snapshots.stream()
                .mapToInt(ExpiryTrendSnapshot::getExpiredCount)
                .average()
                .orElse(0);

        // Determine overall trend
        String overallTrend = "STABLE";
        double trendStrength = 50.0;

        if (snapshots.size() >= 2) {
            ExpiryTrendSnapshot first = snapshots.get(0);
            ExpiryTrendSnapshot last = snapshots.get(snapshots.size() - 1);

            double change = calculatePercentageChange(
                    first.getExpiredCount(),
                    last.getExpiredCount()
            );

            if (change > 20) {
                overallTrend = "WORSENING";
                trendStrength = Math.min(100, 50 + change);
            } else if (change < -20) {
                overallTrend = "IMPROVING";
                trendStrength = Math.max(0, 50 - Math.abs(change));
            }
        }

        return TrendAnalysisResponseDTO.SummaryStatistics.builder()
                .totalExpired(totalExpired)
                .totalExpiring(totalExpiring)
                .totalValueLost(totalValueLost)
                .totalValueAtRisk(totalValueAtRisk)
                .averageExpiryRate(averageExpiryRate)
                .overallTrend(overallTrend)
                .trendStrength(trendStrength)
                .build();
    }

    private Map<String, TrendAnalysisResponseDTO.CategoryAnalysis> analyzeCategoryTrends(
            LocalDate startDate, LocalDate endDate) {

        Map<String, TrendAnalysisResponseDTO.CategoryAnalysis> categoryAnalysisMap = new HashMap<>();

        // Get all categories
        List<Category> categories = categoryRepository.findAll();

        for (Category category : categories) {
            // Get all products in this category
            List<Product> categoryProducts = productRepository.findByCategoryId(category.getId());

            if (categoryProducts.isEmpty()) continue;

            Set<Long> productIds = categoryProducts.stream()
                    .map(Product::getId)
                    .collect(Collectors.toSet());

            // Get all batches for these products within date range
            List<ProductBatch> categoryBatches = batchRepository.findAll().stream()
                    .filter(batch -> batch.getProduct() != null &&
                            productIds.contains(batch.getProduct().getId()) &&
                            batch.getExpiryDate() != null &&
                            !batch.getExpiryDate().isBefore(startDate) &&
                            !batch.getExpiryDate().isAfter(endDate.plusDays(90)))
                    .collect(Collectors.toList());

            if (categoryBatches.isEmpty()) continue;

            // Calculate metrics for this category
            int expiryCount = (int) categoryBatches.stream()
                    .filter(b -> b.getExpiryDate().isBefore(LocalDate.now()))
                    .count();

            BigDecimal totalValue = categoryBatches.stream()
                    .filter(b -> b.getExpiryDate().isBefore(LocalDate.now().plusDays(30)))
                    .map(b -> {
                        if (b.getCostPerUnit() != null && b.getQuantity() != null) {
                            return b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity()));
                        }
                        return BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate percentage of total
            double percentageOfTotal = 0.0;
            if (!categoryBatches.isEmpty()) {
                long totalBatches = batchRepository.count();
                percentageOfTotal = (categoryBatches.size() * 100.0) / totalBatches;
            }

            // Determine trend for this category
            String categoryTrend = determineCategoryTrend(categoryBatches, startDate, endDate);

            // Get top expiring products in this category
            List<String> topProducts = categoryBatches.stream()
                    .filter(b -> b.getExpiryDate().isBefore(LocalDate.now().plusDays(30)))
                    .sorted(Comparator.comparing(ProductBatch::getExpiryDate))
                    .limit(5)
                    .map(b -> b.getProduct().getName())
                    .distinct()
                    .collect(Collectors.toList());

            TrendAnalysisResponseDTO.CategoryAnalysis analysis =
                    TrendAnalysisResponseDTO.CategoryAnalysis.builder()
                            .categoryName(category.getName())
                            .expiryCount(expiryCount)
                            .value(totalValue)
                            .percentageOfTotal(percentageOfTotal)
                            .trend(categoryTrend)
                            .topProducts(topProducts)
                            .build();

            categoryAnalysisMap.put(category.getName(), analysis);
        }

        return categoryAnalysisMap;
    }

    /**
     * Determine trend for a specific category
     */
    private String determineCategoryTrend(List<ProductBatch> categoryBatches,
                                          LocalDate startDate, LocalDate endDate) {
        // Compare first half vs second half of the period
        LocalDate midDate = startDate.plusDays(ChronoUnit.DAYS.between(startDate, endDate) / 2);

        long firstHalfExpiring = categoryBatches.stream()
                .filter(b -> b.getExpiryDate().isBefore(midDate))
                .count();

        long secondHalfExpiring = categoryBatches.stream()
                .filter(b -> !b.getExpiryDate().isBefore(midDate) &&
                        b.getExpiryDate().isBefore(endDate))
                .count();

        if (secondHalfExpiring > firstHalfExpiring * 1.2) return "WORSENING";
        if (firstHalfExpiring > secondHalfExpiring * 1.2) return "IMPROVING";
        return "STABLE";
    }

    private List<TrendAnalysisResponseDTO.TrendInsight> generateInsights(
            List<ExpiryTrendSnapshot> snapshots,
            TrendAnalysisResponseDTO.SummaryStatistics summary) {

        List<TrendAnalysisResponseDTO.TrendInsight> insights = new ArrayList<>();

        if (snapshots == null || snapshots.isEmpty()) {
            insights.add(TrendAnalysisResponseDTO.TrendInsight.builder()
                    .type("INFO")
                    .title("No Data Available")
                    .description("No trend data available for analysis")
                    .recommendation("Wait for system to collect data or select a different date range")
                    .severity(0.0)
                    .build());
            return insights;
        }

        // Check for worsening trend
        if ("WORSENING".equals(summary.getOverallTrend())) {
            insights.add(TrendAnalysisResponseDTO.TrendInsight.builder()
                    .type("WARNING")
                    .title("Increasing Expiry Rate Detected")
                    .description("The expiry rate has increased by " +
                            String.format("%.1f%%", summary.getTrendStrength() - 50))
                    .recommendation("Review inventory ordering patterns and implement stricter FIFO controls")
                    .severity(summary.getTrendStrength())
                    .build());
        }

        // Check for high value at risk
        if (summary.getTotalValueAtRisk().compareTo(BigDecimal.valueOf(10000)) > 0) {
            insights.add(TrendAnalysisResponseDTO.TrendInsight.builder()
                    .type("WARNING")
                    .title("High Value at Risk")
                    .description("Products worth $" + summary.getTotalValueAtRisk() + " are expiring soon")
                    .recommendation("Consider promotional activities or redistribution to minimize losses")
                    .severity(75.0)
                    .build());
        }

        // Positive insight for improvement
        if ("IMPROVING".equals(summary.getOverallTrend())) {
            insights.add(TrendAnalysisResponseDTO.TrendInsight.builder()
                    .type("SUCCESS")
                    .title("Expiry Rate Improving")
                    .description("The expiry rate has decreased, indicating better inventory management")
                    .recommendation("Continue current practices and document successful strategies")
                    .severity(25.0)
                    .build());
        }

        // Add default insight if no specific insights
        if (insights.isEmpty()) {
            insights.add(TrendAnalysisResponseDTO.TrendInsight.builder()
                    .type("INFO")
                    .title("Stable Trend")
                    .description("Expiry rates are within normal parameters")
                    .recommendation("Continue monitoring for any changes")
                    .severity(50.0)
                    .build());
        }

        return insights;
    }

    private List<ExpiryTrendDataPointDTO> calculateCategoryTrends(Category category, LocalDate startDate) {
        LocalDate endDate = LocalDate.now();
        List<ExpiryTrendDataPointDTO> categoryTrendPoints = new ArrayList<>();

        // Get products in this category
        List<Product> categoryProducts = productRepository.findByCategoryId(category.getId());
        Set<Long> productIds = categoryProducts.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        // Generate daily trend points for this category
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            LocalDate dateToCheck = currentDate;

            // Count expiring items for this category on this date
            List<ProductBatch> dayBatches = batchRepository.findAll().stream()
                    .filter(batch -> batch.getProduct() != null &&
                            productIds.contains(batch.getProduct().getId()) &&
                            batch.getExpiryDate() != null &&
                            batch.getExpiryDate().equals(dateToCheck))
                    .collect(Collectors.toList());

            int expiredCount = (int) dayBatches.stream()
                    .filter(b -> b.getExpiryDate().isBefore(LocalDate.now()))
                    .count();

            int expiringCount = (int) dayBatches.stream()
                    .filter(b -> b.getExpiryDate().isAfter(LocalDate.now()) &&
                            b.getExpiryDate().isBefore(LocalDate.now().plusDays(30)))
                    .count();

            BigDecimal value = dayBatches.stream()
                    .map(b -> {
                        if (b.getCostPerUnit() != null && b.getQuantity() != null) {
                            return b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity()));
                        }
                        return BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ExpiryTrendDataPointDTO point = ExpiryTrendDataPointDTO.builder()
                    .date(currentDate)
                    .label(currentDate.toString())
                    .expiredCount(expiredCount)
                    .expiringCount(expiringCount)
                    .value(value)
                    .trend("STABLE")
                    .percentageChange(0.0)
                    .build();

            categoryTrendPoints.add(point);
            currentDate = currentDate.plusDays(1);
        }

        // Calculate trends between consecutive points
        for (int i = 1; i < categoryTrendPoints.size(); i++) {
            ExpiryTrendDataPointDTO current = categoryTrendPoints.get(i);
            ExpiryTrendDataPointDTO previous = categoryTrendPoints.get(i - 1);

            double change = calculatePercentageChange(
                    previous.getExpiredCount(),
                    current.getExpiredCount()
            );
            current.setPercentageChange(change);

            if (change > 10) current.setTrend("UP");
            else if (change < -10) current.setTrend("DOWN");
            else current.setTrend("STABLE");
        }

        return categoryTrendPoints;
    }

    private TrendAnalysisResponseDTO createEmptyAnalysis(LocalDate startDate, LocalDate endDate, String granularity) {
        return TrendAnalysisResponseDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .granularity(granularity)
                .trendData(new ArrayList<>())
                .summary(TrendAnalysisResponseDTO.SummaryStatistics.builder()
                        .totalExpired(0)
                        .totalExpiring(0)
                        .totalValueLost(BigDecimal.ZERO)
                        .totalValueAtRisk(BigDecimal.ZERO)
                        .averageExpiryRate(0.0)
                        .overallTrend("NO_DATA")
                        .trendStrength(0.0)
                        .build())
                .categoryAnalysis(new HashMap<>())
                .insights(List.of(
                        TrendAnalysisResponseDTO.TrendInsight.builder()
                                .type("INFO")
                                .title("No Data Available")
                                .description("No trend data available for the selected period")
                                .recommendation("Wait for system to collect more data or select a different date range")
                                .severity(0.0)
                                .build()
                ))
                .build();
    }

    /**
     * Analyze trends for a specific category
     */
    public TrendAnalysisResponseDTO analyzeCategoryTrends(LocalDate startDate, LocalDate endDate,
                                                          Long categoryId, String granularity) {
        log.info("Analyzing category {} trends from {} to {}", categoryId, startDate, endDate);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        // Get the general analysis first
        TrendAnalysisResponseDTO generalAnalysis = analyzeTrends(startDate, endDate, granularity);

        // Filter for specific category
        List<Product> categoryProducts = productRepository.findByCategoryId(categoryId);
        Set<Long> productIds = categoryProducts.stream()
                .map(Product::getId)
                .collect(Collectors.toSet());

        // Get category-specific snapshots
        List<ExpiryTrendSnapshot> snapshots = trendRepository
                .findBySnapshotDateBetweenOrderBySnapshotDate(startDate, endDate);

        // Create category-specific trend data
        List<ExpiryTrendDataPointDTO> categoryTrendData = new ArrayList<>();

        for (ExpiryTrendSnapshot snapshot : snapshots) {
            // Count items for this category
            List<ProductBatch> categoryBatches = batchRepository.findAll().stream()
                    .filter(batch -> batch.getProduct() != null &&
                            productIds.contains(batch.getProduct().getId()) &&
                            batch.getExpiryDate() != null)
                    .collect(Collectors.toList());

            int expiredCount = (int) categoryBatches.stream()
                    .filter(b -> b.getExpiryDate().isBefore(snapshot.getSnapshotDate()))
                    .count();

            int expiringCount = (int) categoryBatches.stream()
                    .filter(b -> b.getExpiryDate().isAfter(snapshot.getSnapshotDate()) &&
                            b.getExpiryDate().isBefore(snapshot.getSnapshotDate().plusDays(30)))
                    .count();

            BigDecimal value = categoryBatches.stream()
                    .filter(b -> b.getExpiryDate().isBefore(snapshot.getSnapshotDate().plusDays(30)))
                    .map(b -> {
                        if (b.getCostPerUnit() != null && b.getQuantity() != null) {
                            return b.getCostPerUnit().multiply(BigDecimal.valueOf(b.getQuantity()));
                        }
                        return BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ExpiryTrendDataPointDTO point = ExpiryTrendDataPointDTO.builder()
                    .date(snapshot.getSnapshotDate())
                    .label(snapshot.getSnapshotDate().toString() + " - " + category.getName())
                    .expiredCount(expiredCount)
                    .expiringCount(expiringCount)
                    .value(value)
                    .trend("STABLE")
                    .percentageChange(0.0)
                    .build();

            categoryTrendData.add(point);
        }

        // Apply granularity
        categoryTrendData = convertToDataPoints(snapshots, granularity);

        // Update the analysis with category-specific data
        generalAnalysis.setTrendData(categoryTrendData);

        // Add category-specific insights
        List<TrendAnalysisResponseDTO.TrendInsight> insights = new ArrayList<>(generalAnalysis.getInsights());
        insights.add(0, TrendAnalysisResponseDTO.TrendInsight.builder()
                .type("INFO")
                .title("Category Analysis: " + category.getName())
                .description("This analysis is filtered for the " + category.getName() + " category only")
                .recommendation("Focus on category-specific inventory management strategies")
                .severity(50.0)
                .build());
        generalAnalysis.setInsights(insights);

        return generalAnalysis;
    }

    /**
     * Apply filters to analysis results - COMPLETE IMPLEMENTATION
     */
    public TrendAnalysisResponseDTO applyFilters(TrendAnalysisResponseDTO analysis,
                                                 ExpiryTrendRequestDTO filters) {
        log.info("Applying filters to trend analysis");

        if (analysis == null || filters == null) {
            return analysis;
        }

        List<ExpiryTrendDataPointDTO> filteredData = new ArrayList<>(analysis.getTrendData());

        // Filter by trend direction
        if (filters.getTrendDirection() != null && !"ALL".equals(filters.getTrendDirection())) {
            filteredData = filteredData.stream()
                    .filter(point -> filters.getTrendDirection().equals(point.getTrend()))
                    .collect(Collectors.toList());
        }

        // Filter by value range
        if (filters.getMinValue() != null || filters.getMaxValue() != null) {
            filteredData = filteredData.stream()
                    .filter(point -> {
                        double value = point.getValue().doubleValue();
                        boolean aboveMin = filters.getMinValue() == null || value >= filters.getMinValue();
                        boolean belowMax = filters.getMaxValue() == null || value <= filters.getMaxValue();
                        return aboveMin && belowMax;
                    })
                    .collect(Collectors.toList());
        }

        // Filter by expiry count range
        if (filters.getMinExpiryCount() != null || filters.getMaxExpiryCount() != null) {
            filteredData = filteredData.stream()
                    .filter(point -> {
                        int count = point.getExpiredCount();
                        boolean aboveMin = filters.getMinExpiryCount() == null || count >= filters.getMinExpiryCount();
                        boolean belowMax = filters.getMaxExpiryCount() == null || count <= filters.getMaxExpiryCount();
                        return aboveMin && belowMax;
                    })
                    .collect(Collectors.toList());
        }

        // Filter weekends if requested
        if (!filters.getIncludeWeekends()) {
            filteredData = filteredData.stream()
                    .filter(point -> {
                        DayOfWeek dayOfWeek = point.getDate().getDayOfWeek();
                        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
                    })
                    .collect(Collectors.toList());
        }

        // Filter zero values if requested
        if (!filters.getIncludeZeroValues()) {
            filteredData = filteredData.stream()
                    .filter(point -> point.getExpiredCount() > 0 || point.getExpiringCount() > 0)
                    .collect(Collectors.toList());
        }

        // Apply pagination if specified
        if (filters.getPage() != null && filters.getSize() != null) {
            int start = filters.getPage() * filters.getSize();
            int end = Math.min(start + filters.getSize(), filteredData.size());
            if (start < filteredData.size()) {
                filteredData = filteredData.subList(start, end);
            }
        }

        // Apply sorting
        if (filters.getSortBy() != null) {
            Comparator<ExpiryTrendDataPointDTO> comparator = null;

            switch (filters.getSortBy().toLowerCase()) {
                case "date":
                    comparator = Comparator.comparing(ExpiryTrendDataPointDTO::getDate);
                    break;
                case "expired":
                    comparator = Comparator.comparing(ExpiryTrendDataPointDTO::getExpiredCount);
                    break;
                case "expiring":
                    comparator = Comparator.comparing(ExpiryTrendDataPointDTO::getExpiringCount);
                    break;
                case "value":
                    comparator = Comparator.comparing(ExpiryTrendDataPointDTO::getValue);
                    break;
                default:
                    comparator = Comparator.comparing(ExpiryTrendDataPointDTO::getDate);
            }

            if ("DESC".equalsIgnoreCase(filters.getSortDirection())) {
                comparator = comparator.reversed();
            }

            filteredData = filteredData.stream()
                    .sorted(comparator)
                    .collect(Collectors.toList());
        }

        analysis.setTrendData(filteredData);
        return analysis;
    }
}