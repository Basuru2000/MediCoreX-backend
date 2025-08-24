package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiryCalendarEventDTO {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private EventType type;
    private EventSeverity severity;
    private String title;
    private String description;
    private Integer itemCount;
    private Integer totalQuantity;
    private BigDecimal totalValue;
    private String color;
    private String icon;
    private List<EventDetail> details;
    private String actionUrl;
    private Boolean isToday;
    private Boolean isPast;
    private Integer daysUntil;

    public enum EventType {
        BATCH_EXPIRY,
        PRODUCT_EXPIRY,
        ALERT,
        QUARANTINE,
        MULTIPLE
    }

    public enum EventSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetail {
        private Long itemId;
        private String itemType;
        private String itemName;
        private String batchNumber;
        private Integer quantity;
        private BigDecimal value;
        private String category;
        private String severity;
        private String actionUrl;
    }
}