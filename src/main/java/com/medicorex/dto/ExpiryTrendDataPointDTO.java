package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryTrendDataPointDTO {
    private LocalDate date;
    private String label; // For display
    private Integer expiredCount;
    private Integer expiringCount;
    private BigDecimal value;
    private String trend; // UP, DOWN, STABLE
    private Double percentageChange;

    // For chart plotting
    private Integer x; // Days from start
    private Integer y; // Value for Y-axis
}