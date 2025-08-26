package com.medicorex.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketMessage {

    public enum MessageType {
        NOTIFICATION,
        NOTIFICATION_UPDATE,
        ALERT,
        STOCK_UPDATE,
        BATCH_UPDATE,
        QUARANTINE_UPDATE,
        SYSTEM_MESSAGE,
        HEARTBEAT,
        CONNECTION_ACK
    }

    private String id;
    private MessageType type;
    private String category;
    private String priority;
    private String title;
    private String message;
    private Map<String, Object> data;
    private String actionUrl;
    private LocalDateTime timestamp;
    private String senderId;
    private String recipientId;
    private boolean broadcast;
}