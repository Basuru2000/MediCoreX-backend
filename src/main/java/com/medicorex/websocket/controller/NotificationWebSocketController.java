package com.medicorex.websocket.controller;

import com.medicorex.websocket.dto.WebSocketMessage;
import com.medicorex.websocket.dto.WebSocketNotification;
import com.medicorex.websocket.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationWebSocketController {

    private final WebSocketNotificationService webSocketService;

    /**
     * Handle incoming messages from client
     */
    @MessageMapping("/notification.send")
    @SendTo("/topic/notifications")
    public WebSocketMessage sendNotification(@Payload WebSocketMessage message, Principal principal) {
        log.info("Received WebSocket message from {}: {}", principal.getName(), message.getType());

        message.setSenderId(principal.getName());
        message.setTimestamp(LocalDateTime.now());

        return message;
    }

    /**
     * Handle private messages to specific user
     */
    @MessageMapping("/notification.private")
    @SendToUser("/queue/notifications")
    public WebSocketNotification sendPrivateNotification(
            @Payload WebSocketNotification notification,
            Principal principal) {

        log.info("Private notification from {}: {}", principal.getName(), notification.getEventType());

        notification.setUsername(principal.getName());
        notification.setTimestamp(LocalDateTime.now());

        return notification;
    }

    /**
     * Mark notification as read via WebSocket
     */
    @MessageMapping("/notification.read")
    public void markAsRead(@Payload Map<String, Object> payload, Principal principal) {
        Long notificationId = Long.valueOf(payload.get("notificationId").toString());
        log.info("User {} marked notification {} as read via WebSocket",
                principal.getName(), notificationId);

        // Broadcast read status to user's other sessions
        WebSocketNotification readNotification = WebSocketNotification.notificationRead(
                notificationId,
                principal.getName(),
                null // Will be updated by service
        );

        webSocketService.sendNotificationToUser(principal.getName(), null, null);
    }

    /**
     * Handle connection event
     */
    @MessageMapping("/connect")
    @SendToUser("/queue/system")
    public Map<String, Object> handleConnect(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String username = principal.getName();
        log.info("WebSocket connect request from: {}", username);

        webSocketService.handleUserConnection(username);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "connected");
        response.put("username", username);
        response.put("timestamp", LocalDateTime.now());
        response.put("sessionId", headerAccessor.getSessionId());

        return response;
    }

    /**
     * Handle disconnect event
     */
    @MessageMapping("/disconnect")
    public void handleDisconnect(Principal principal) {
        String username = principal.getName();
        log.info("WebSocket disconnect request from: {}", username);

        webSocketService.handleUserDisconnection(username);
    }

    /**
     * Get connection status
     */
    @MessageMapping("/status")
    @SendToUser("/queue/system")
    public Map<String, Object> getStatus(Principal principal) {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", true);
        status.put("username", principal.getName());
        status.put("timestamp", LocalDateTime.now());
        status.put("connectedUsers", webSocketService.getConnectedUsers().size());

        return status;
    }

    /**
     * Scheduled heartbeat to keep connections alive
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void sendHeartbeat() {
        webSocketService.sendHeartbeat();
    }
}