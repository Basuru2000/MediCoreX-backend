package com.medicorex.dto;

import com.medicorex.entity.Notification.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationFilterDTO {
    private NotificationStatus status;
    private NotificationCategory category;
    private NotificationPriority priority;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String searchText;
    private Integer page = 0;
    private Integer size = 10;
}