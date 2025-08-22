package com.medicorex.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryTrendRequestDTO {

    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Pattern(regexp = "DAILY|WEEKLY|MONTHLY", message = "Granularity must be DAILY, WEEKLY, or MONTHLY")
    @Builder.Default
    private String granularity = "DAILY";

    @Min(value = 1, message = "Days back must be at least 1")
    @Max(value = 365, message = "Days back cannot exceed 365")
    @Builder.Default
    private Integer daysBack = 30;

    @Min(value = 1, message = "Days ahead must be at least 1")
    @Max(value = 90, message = "Days ahead cannot exceed 90")
    @Builder.Default
    private Integer daysAhead = 30;

    // For category-specific analysis
    private Long categoryId;
    private String categoryName;

    // For comparison requests
    private LocalDate comparePeriodStart;
    private LocalDate comparePeriodEnd;

    // Export options
    @Builder.Default
    private String exportFormat = "CSV"; // CSV, JSON, PDF
    @Builder.Default
    private Boolean includeCharts = false;
    @Builder.Default
    private Boolean includePredictions = true;
    @Builder.Default
    private Boolean includeInsights = true;
    @Builder.Default
    private Boolean includeCategoryBreakdown = true;

    // Filter options
    private String trendDirection; // IMPROVING, STABLE, WORSENING, ALL
    private Double minValue;
    private Double maxValue;
    private Integer minExpiryCount;
    private Integer maxExpiryCount;

    // Aggregation options
    @Builder.Default
    private Boolean includeZeroValues = false;
    @Builder.Default
    private Boolean includeWeekends = true;
    @Builder.Default
    private Boolean interpolateMissingData = true;

    // Pagination for large datasets
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 100;
    @Builder.Default
    private String sortBy = "date";
    @Builder.Default
    private String sortDirection = "ASC";

    // Advanced analysis options
    @Builder.Default
    private String predictionAlgorithm = "MOVING_AVERAGE"; // MOVING_AVERAGE, LINEAR_REGRESSION, EXPONENTIAL_SMOOTHING
    @Builder.Default
    private Integer movingAveragePeriod = 7;
    @Builder.Default
    private Double exponentialSmoothingAlpha = 0.3;
    @Builder.Default
    private Double confidenceLevel = 0.95;

    // Validation methods
    public boolean isValidDateRange() {
        return startDate != null && endDate != null && !startDate.isAfter(endDate);
    }

    public boolean isComparisonRequest() {
        return comparePeriodStart != null && comparePeriodEnd != null;
    }

    public boolean hasFilters() {
        return trendDirection != null || minValue != null || maxValue != null ||
                minExpiryCount != null || maxExpiryCount != null;
    }

    public boolean isCategorySpecific() {
        return categoryId != null || categoryName != null;
    }

    // Helper method to get date range in days
    public long getDateRangeInDays() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }

    // Helper method to determine optimal granularity
    public String getOptimalGranularity() {
        long days = getDateRangeInDays();
        if (days <= 7) {
            return "DAILY";
        } else if (days <= 90) {
            return granularity != null ? granularity : "DAILY";
        } else if (days <= 365) {
            return "WEEKLY";
        } else {
            return "MONTHLY";
        }
    }
}