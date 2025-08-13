package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationBatchDTO {
    private List<Long> userIds;
    private String templateCode;
    private Map<String, String> params;
    private Map<String, Object> actionData;
    private String customTitle;
    private String customMessage;
}