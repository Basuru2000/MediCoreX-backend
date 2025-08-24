package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.time.LocalDate;
import java.time.YearMonth;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryCalendarRequestDTO {

    private YearMonth month;
    private LocalDate startDate;
    private LocalDate endDate;

    @Builder.Default
    private ViewType viewType = ViewType.MONTH;

    @Min(1)
    @Max(90)
    @Builder.Default
    private Integer daysAhead = 30;

    @Builder.Default
    private Boolean includeResolved = false;

    @Builder.Default
    private Boolean includeQuarantined = true;

    private Long categoryId;
    private String severity;
    private String eventType;

    @Builder.Default
    private Boolean groupByDate = true;

    public enum ViewType {
        DAY,
        WEEK,
        MONTH,
        QUARTER
    }
}