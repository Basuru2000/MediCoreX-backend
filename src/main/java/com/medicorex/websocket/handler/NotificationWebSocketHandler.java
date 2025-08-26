package com.medicorex.websocket.handler;

import com.medicorex.websocket.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket event handler for managing connection lifecycle and session tracking
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler {

    private final WebSocketNotificationService webSocketService;
    private final SimpMessagingTemplate messagingTemplate;

    // Track active sessions
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    /**
     * Session information holder
     */
    private static class SessionInfo {
        String username;
        String sessionId;
        LocalDateTime connectedAt;
        LocalDateTime lastActivity;
        int subscriptionCount;

        SessionInfo(String username, String sessionId) {
            this.username = username;
            this.sessionId = sessionId;
            this.connectedAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
            this.subscriptionCount = 0;
        }

        void updateActivity() {
            this.lastActivity = LocalDateTime.now();
        }
    }

    /**
     * Handle WebSocket connection request
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();

        if (user != null) {
            log.info("WebSocket connection request from user: {} with session: {}",
                    user.getName(), sessionId);

            // Track session
            SessionInfo sessionInfo = new SessionInfo(user.getName(), sessionId);
            activeSessions.put(sessionId, sessionInfo);
        } else {
            log.warn("WebSocket connection request with no user principal for session: {}", sessionId);
        }
    }

    /**
     * Handle successful WebSocket connection
     */
    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();

        if (user != null) {
            String username = user.getName();
            log.info("WebSocket connected successfully - User: {}, Session: {}", username, sessionId);

            // Update session info
            SessionInfo sessionInfo = activeSessions.get(sessionId);
            if (sessionInfo != null) {
                sessionInfo.updateActivity();
            }

            // Notify service
            webSocketService.handleUserConnection(username);

            // Send welcome message
            sendWelcomeMessage(username, sessionId);

            // Send pending notifications
            sendPendingNotifications(username);
        }
    }

    /**
     * Handle WebSocket disconnection
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        SessionInfo sessionInfo = activeSessions.remove(sessionId);

        if (sessionInfo != null) {
            log.info("WebSocket disconnected - User: {}, Session: {}, Duration: {} seconds",
                    sessionInfo.username,
                    sessionId,
                    java.time.Duration.between(sessionInfo.connectedAt, LocalDateTime.now()).getSeconds());

            // Notify service
            webSocketService.handleUserDisconnection(sessionInfo.username);

            // Check if user has other active sessions
            boolean hasOtherSessions = activeSessions.values().stream()
                    .anyMatch(info -> info.username.equals(sessionInfo.username));

            if (!hasOtherSessions) {
                log.info("User {} has no more active WebSocket sessions", sessionInfo.username);
                // Could trigger offline status update here
            }
        } else {
            log.warn("WebSocket disconnected for unknown session: {}", sessionId);
        }
    }

    /**
     * Handle channel subscription
     */
    @EventListener
    public void handleSubscription(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        Principal user = headerAccessor.getUser();

        if (user != null && destination != null) {
            log.debug("User {} subscribed to {} in session {}",
                    user.getName(), destination, sessionId);

            SessionInfo sessionInfo = activeSessions.get(sessionId);
            if (sessionInfo != null) {
                sessionInfo.subscriptionCount++;
                sessionInfo.updateActivity();
            }
        }
    }

    /**
     * Handle channel unsubscription
     */
    @EventListener
    public void handleUnsubscription(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();
        Principal user = headerAccessor.getUser();

        if (user != null) {
            log.debug("User {} unsubscribed from subscription {} in session {}",
                    user.getName(), subscriptionId, sessionId);

            SessionInfo sessionInfo = activeSessions.get(sessionId);
            if (sessionInfo != null && sessionInfo.subscriptionCount > 0) {
                sessionInfo.subscriptionCount--;
                sessionInfo.updateActivity();
            }
        }
    }

    /**
     * Send welcome message to newly connected user
     */
    private void sendWelcomeMessage(String username, String sessionId) {
        try {
            Map<String, Object> welcomeData = new HashMap<>();
            welcomeData.put("type", "WELCOME");
            welcomeData.put("message", "Connected to notification service");
            welcomeData.put("username", username);
            welcomeData.put("sessionId", sessionId);
            welcomeData.put("timestamp", LocalDateTime.now());
            welcomeData.put("serverTime", LocalDateTime.now());

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/system",
                    welcomeData
            );

            log.debug("Sent welcome message to user: {}", username);
        } catch (Exception e) {
            log.error("Failed to send welcome message to user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Send any pending notifications to reconnected user
     */
    private void sendPendingNotifications(String username) {
        try {
            // This would typically query for undelivered notifications
            // For now, just send a notification count update
            Map<String, Object> pendingInfo = new HashMap<>();
            pendingInfo.put("type", "PENDING_CHECK");
            pendingInfo.put("message", "Checking for pending notifications");
            pendingInfo.put("timestamp", LocalDateTime.now());

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/system",
                    pendingInfo
            );

            log.debug("Triggered pending notification check for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to send pending notifications to user {}: {}", username, e.getMessage());
        }
    }

    /**
     * Get current session statistics
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", activeSessions.size());
        stats.put("uniqueUsers", activeSessions.values().stream()
                .map(info -> info.username)
                .distinct()
                .count());
        stats.put("activeSessions", activeSessions.values().stream()
                .filter(info -> info.lastActivity.isAfter(LocalDateTime.now().minusMinutes(5)))
                .count());

        return stats;
    }

    /**
     * Check if a user has active WebSocket sessions
     */
    public boolean isUserConnected(String username) {
        return activeSessions.values().stream()
                .anyMatch(info -> info.username.equals(username));
    }

    /**
     * Get all active sessions for a user
     */
    public long getUserSessionCount(String username) {
        return activeSessions.values().stream()
                .filter(info -> info.username.equals(username))
                .count();
    }

    /**
     * Force disconnect a user's sessions (admin function)
     */
    public void forceDisconnectUser(String username) {
        activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().username.equals(username))
                .forEach(entry -> {
                    log.info("Force disconnecting session {} for user {}",
                            entry.getKey(), username);
                    // Send disconnect message
                    messagingTemplate.convertAndSendToUser(
                            username,
                            "/queue/system",
                            Map.of(
                                    "type", "FORCE_DISCONNECT",
                                    "message", "Session terminated by administrator",
                                    "timestamp", LocalDateTime.now()
                            )
                    );
                });
    }
}