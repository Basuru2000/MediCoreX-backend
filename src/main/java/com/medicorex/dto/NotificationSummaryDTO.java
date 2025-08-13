package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSummaryDTO {
    private Long totalCount;
    private Long unreadCount;
    private Long criticalCount;
    private Long highPriorityCount;
    private Long todayCount;

    // ADD THIS CONSTRUCTOR for JPQL queries
    public NotificationSummaryDTO(Long totalCount, Long unreadCount, Long criticalCount) {
        this.totalCount = totalCount != null ? totalCount : 0L;
        this.unreadCount = unreadCount != null ? unreadCount : 0L;
        this.criticalCount = criticalCount != null ? criticalCount : 0L;
        this.highPriorityCount = 0L;
        this.todayCount = 0L;
    }
}