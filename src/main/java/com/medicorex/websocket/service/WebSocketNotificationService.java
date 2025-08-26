package com.medicorex.websocket.service;

import com.medicorex.dto.NotificationDTO;
import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.websocket.dto.WebSocketMessage;
import com.medicorex.websocket.dto.WebSocketNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final UserRepository userRepository;

    // Track active connections
    private final Map<String, LocalDateTime> activeConnections = new ConcurrentHashMap<>();

    /**
     * Send notification to specific user
     */
    public void sendNotificationToUser(String username, NotificationDTO notification, Integer unreadCount) {
        try {
            WebSocketNotification wsNotification = WebSocketNotification.newNotification(
                    notification,
                    username,
                    unreadCount
            );

            String destination = "/queue/notifications";
            messagingTemplate.convertAndSendToUser(username, destination, wsNotification);

            log.info("Sent WebSocket notification to user: {} at {}", username, destination);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Send notification to users by role
     */
    public void sendNotificationToRole(String role, NotificationDTO notification) {
        try {
            List<User> users = userRepository.findByRoleAndActiveTrue(User.UserRole.valueOf(role));

            for (User user : users) {
                if (isUserConnected(user.getUsername())) {
                    sendNotificationToUser(user.getUsername(), notification, null);
                }
            }

            log.info("Sent WebSocket notification to {} users with role: {}", users.size(), role);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to role {}: {}", role, e.getMessage());
        }
    }

    /**
     * Broadcast notification to all connected users
     */
    public void broadcastNotification(WebSocketMessage message) {
        try {
            message.setBroadcast(true);
            message.setTimestamp(LocalDateTime.now());

            messagingTemplate.convertAndSend("/topic/broadcast", message);

            log.info("Broadcast WebSocket message of type: {}", message.getType());
        } catch (Exception e) {
            log.error("Failed to broadcast WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * Send system alert to all managers
     */
    public void sendSystemAlert(String title, String message, Map<String, Object> data) {
        WebSocketMessage wsMessage = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.SYSTEM_MESSAGE)
                .category("SYSTEM")
                .priority("HIGH")
                .title(title)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

        List<User> managers = userRepository.findByRoleAndActiveTrue(User.UserRole.HOSPITAL_MANAGER);

        for (User manager : managers) {
            messagingTemplate.convertAndSendToUser(
                    manager.getUsername(),
                    "/queue/alerts",
                    wsMessage
            );
        }
    }

    /**
     * Send stock update notification
     */
    public void sendStockUpdate(Long productId, String productName, Integer newQuantity, String username) {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", productId);
        data.put("productName", productName);
        data.put("newQuantity", newQuantity);

        WebSocketMessage message = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.STOCK_UPDATE)
                .category("STOCK")
                .title("Stock Update")
                .message(String.format("%s stock updated to %d", productName, newQuantity))
                .data(data)
                .actionUrl("/products/" + productId)
                .timestamp(LocalDateTime.now())
                .build();

        if (username != null) {
            messagingTemplate.convertAndSendToUser(username, "/queue/updates", message);
        } else {
            broadcastNotification(message);
        }
    }

    /**
     * Send quarantine alert
     */
    public void sendQuarantineAlert(Long batchId, String batchNumber, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", batchId);
        data.put("batchNumber", batchNumber);
        data.put("reason", reason);

        WebSocketMessage message = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.QUARANTINE_UPDATE)
                .category("QUARANTINE")
                .priority("HIGH")
                .title("Quarantine Alert")
                .message(String.format("Batch %s has been quarantined: %s", batchNumber, reason))
                .data(data)
                .actionUrl("/quarantine")
                .timestamp(LocalDateTime.now())
                .build();

        broadcastNotification(message);
    }

    /**
     * Handle user connection
     */
    public void handleUserConnection(String username) {
        activeConnections.put(username, LocalDateTime.now());
        log.info("User connected via WebSocket: {}", username);

        // Send connection acknowledgment
        WebSocketMessage ack = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.CONNECTION_ACK)
                .message("Connected successfully")
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSendToUser(username, "/queue/system", ack);
    }

    /**
     * Handle user disconnection
     */
    public void handleUserDisconnection(String username) {
        activeConnections.remove(username);
        log.info("User disconnected from WebSocket: {}", username);
    }

    /**
     * Check if user is connected
     */
    public boolean isUserConnected(String username) {
        return userRegistry.getUser(username) != null &&
                !userRegistry.getUser(username).getSessions().isEmpty();
    }

    /**
     * Get list of connected users
     */
    public List<String> getConnectedUsers() {
        return userRegistry.getUsers().stream()
                .map(user -> user.getName())
                .collect(Collectors.toList());
    }

    /**
     * Send heartbeat to keep connections alive
     */
    public void sendHeartbeat() {
        WebSocketMessage heartbeat = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.HEARTBEAT)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/heartbeat", heartbeat);
    }
}