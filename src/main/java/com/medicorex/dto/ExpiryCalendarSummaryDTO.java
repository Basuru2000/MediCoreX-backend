package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryCalendarSummaryDTO {

    private Integer totalExpiringItems;
    private Integer totalBatchesExpiring;
    private Integer totalProductsExpiring;
    private Integer totalAlerts;
    private BigDecimal totalValueAtRisk;

    private Map<String, Integer> eventsByType;
    private Map<String, Integer> eventsBySeverity;
    private Map<String, BigDecimal> valueByCategory;

    private WeekSummary thisWeek;
    private WeekSummary nextWeek;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekSummary {
        private Integer itemCount;
        private Integer batchCount;
        private Integer alertCount;
        private BigDecimal valueAtRisk;
        private String mostCriticalItem;
    }
}