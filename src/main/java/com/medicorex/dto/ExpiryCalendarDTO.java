package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryCalendarDTO {

    private YearMonth currentMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private Map<LocalDate, List<ExpiryCalendarEventDTO>> events;
    private ExpiryCalendarSummaryDTO summary;
    private List<LocalDate> criticalDates;
    private CalendarMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarMetadata {
        private Integer totalEvents;
        private Integer criticalEvents;
        private Integer highPriorityEvents;
        private Integer mediumPriorityEvents;
        private Integer lowPriorityEvents;
        private LocalDate nextCriticalDate;
        private Boolean hasToday;
    }
}