package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpiryCheckResultDTO {
    private Long checkLogId;
    private LocalDate checkDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer productsChecked;
    private Integer alertsGenerated;
    private Long executionTimeMs;
    private String errorMessage;

    // Breakdown by severity
    private Map<String, Integer> alertsBySeverity;

    // Breakdown by days until expiry
    private Map<String, Integer> alertsByDaysRange;
}