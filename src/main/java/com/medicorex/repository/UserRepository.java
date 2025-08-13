package com.medicorex.repository;

import com.medicorex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Existing methods
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // Fixed methods for notification support
    List<User> findByRole(String role);

    // Fixed: Get users by multiple roles
    List<User> findByRoleIn(List<String> roles);

    // Additional useful methods for notifications

    // Find users by role who have notifications enabled (for future use with preferences)
    @Query("SELECT u FROM User u WHERE u.role IN :roles")
    List<User> findUsersByRoles(@Param("roles") List<String> roles);

    // Get user with notification count
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdWithNotifications(@Param("userId") Long userId);

    // Update unread notification count
    @Query("UPDATE User u SET u.unreadNotifications = :count WHERE u.id = :userId")
    void updateUnreadNotificationCount(@Param("userId") Long userId, @Param("count") Integer count);
}