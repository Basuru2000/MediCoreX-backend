package com.medicorex.repository;

import com.medicorex.dto.NotificationSummaryDTO;
import com.medicorex.entity.Notification;
import com.medicorex.entity.Notification.NotificationCategory;
import com.medicorex.entity.Notification.NotificationStatus;
import com.medicorex.entity.Notification.NotificationPriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Find by user
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Find by user and status
    Page<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, NotificationStatus status, Pageable pageable);

    // Find unread notifications
    List<Notification> findByUserIdAndStatus(Long userId, NotificationStatus status);

    // Count unread
    Long countByUserIdAndStatus(Long userId, NotificationStatus status);

    // Find by category
    Page<Notification> findByUserIdAndCategoryOrderByCreatedAtDesc(
            Long userId, NotificationCategory category, Pageable pageable);

    // Find high priority unread
    List<Notification> findByUserIdAndStatusAndPriorityIn(
            Long userId,
            NotificationStatus status,
            List<NotificationPriority> priorities);

    // Mark as read
    @Modifying
    @Query("UPDATE Notification n SET n.status = :status, n.readAt = :readAt WHERE n.id = :id")
    void updateStatus(@Param("id") Long id,
                      @Param("status") NotificationStatus status,
                      @Param("readAt") LocalDateTime readAt);

    // Mark all as read for user
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt " +
            "WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    void markAllAsReadForUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // Delete old archived notifications
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.status = 'ARCHIVED' " +
            "AND n.createdAt < :cutoffDate")
    void deleteOldArchivedNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Delete expired notifications
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.expiresAt IS NOT NULL " +
            "AND n.expiresAt < :now")
    void deleteExpiredNotifications(@Param("now") LocalDateTime now);

    // Find recent critical notifications
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId " +
            "AND n.priority = 'CRITICAL' AND n.status = 'UNREAD' " +
            "AND n.createdAt > :since ORDER BY n.createdAt DESC")
    List<Notification> findRecentCriticalNotifications(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since);

    // Fixed query that handles empty tables better
    @Query("SELECT COUNT(n), " +
            "COALESCE(SUM(CASE WHEN n.status = 'UNREAD' THEN 1 ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN n.priority = 'CRITICAL' AND n.status = 'UNREAD' THEN 1 ELSE 0 END), 0) " +
            "FROM Notification n WHERE n.user.id = :userId")
    Object[] getNotificationSummary(@Param("userId") Long userId);

    // Alternative: Return a custom DTO directly
    @Query("SELECT new com.medicorex.dto.NotificationSummaryDTO(" +
            "COUNT(n), " +
            "COALESCE(SUM(CASE WHEN n.status = 'UNREAD' THEN 1 ELSE 0 END), 0L), " +
            "COALESCE(SUM(CASE WHEN n.priority = 'CRITICAL' AND n.status = 'UNREAD' THEN 1 ELSE 0 END), 0L), " +
            "0L, " +  // highPriorityCount
            "0L) " +  // todayCount
            "FROM Notification n WHERE n.user.id = :userId")
    NotificationSummaryDTO getNotificationSummaryDTO(@Param("userId") Long userId);

    // Find by action URL
    Optional<Notification> findByUserIdAndActionUrl(Long userId, String actionUrl);

    // Check if similar notification exists (to avoid duplicates)
    @Query("SELECT COUNT(n) > 0 FROM Notification n " +
            "WHERE n.user.id = :userId AND n.type = :type " +
            "AND n.status = 'UNREAD' AND n.createdAt > :since")
    boolean existsSimilarUnreadNotification(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("since") LocalDateTime since);

    /**
     * Dynamic query for filtering notifications
     * This single query handles all filter combinations
     */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId " +
            "AND (:status IS NULL OR n.status = :status) " +
            "AND (:category IS NULL OR n.category = :category) " +
            "AND (:priority IS NULL OR n.priority = :priority) " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("status") NotificationStatus status,
            @Param("category") NotificationCategory category,
            @Param("priority") NotificationPriority priority,
            Pageable pageable);

    // Cleanup methods
    int deleteByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime date);

    int deleteByExpiresAtBefore(LocalDateTime date);

    int deleteByStatusAndReadAtBefore(NotificationStatus status, LocalDateTime date);

    // Count methods for summaries
    Long countByUserIdAndStatusAndPriority(Long userId, NotificationStatus status, NotificationPriority priority);

    // Count by user, status, and category
    Long countByUserIdAndStatusAndCategory(Long userId, NotificationStatus status, NotificationCategory category);

    // Find for escalation
    List<Notification> findByStatusAndPriorityAndCreatedAtBefore(
            NotificationStatus status,
            NotificationPriority priority,
            LocalDateTime date
    );

    // Find recent notifications for grouping
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId " +
            "AND n.status = 'UNREAD' AND n.createdAt > :since " +
            "ORDER BY n.type, n.category, n.createdAt DESC")
    List<Notification> findRecentUnreadForGrouping(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    // Check for duplicate notifications
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId " +
            "AND n.type = :type AND n.status = 'UNREAD' " +
            "AND n.createdAt > :since AND n.title = :title")
    Long countDuplicateNotifications(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("since") LocalDateTime since
    );

    /**
     * Find unescalated critical notifications
     */
    @Query("SELECT n FROM Notification n WHERE n.createdAt < :thresholdTime " +
            "AND n.priority = :priority AND n.status = :status")
    List<Notification> findUnescalatedCritical(
            @Param("thresholdTime") LocalDateTime thresholdTime,
            @Param("priority") NotificationPriority priority,
            @Param("status") NotificationStatus status
    );

    /**
     * Delete old archived notifications
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate AND n.status = :status")
    int deleteByCreatedAtBeforeAndStatus(
            @Param("cutoffDate") LocalDateTime cutoffDate,
            @Param("status") NotificationStatus status
    );

    /**
     * Find archived notifications older than specified date for cleanup
     */
    List<Notification> findByCreatedAtBeforeAndStatus(LocalDateTime cutoffDate, NotificationStatus status);

    /**
     * Find expired notifications that are not archived
     */
    List<Notification> findByExpiresAtBeforeAndStatusNot(LocalDateTime now, NotificationStatus status);

    /**
     * Find old read notifications for cleanup
     */
    List<Notification> findByReadAtBeforeAndStatus(LocalDateTime cutoffDate, NotificationStatus status);
}