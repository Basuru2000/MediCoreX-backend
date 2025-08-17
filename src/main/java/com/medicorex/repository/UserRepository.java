package com.medicorex.repository;

import com.medicorex.entity.User;
import com.medicorex.entity.User.UserRole;  // Using UserRole
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Find by single role
    List<User> findByRole(UserRole role);  // Changed to UserRole

    // Find by multiple roles
    List<User> findByRoleIn(List<UserRole> roles);  // Changed to UserRole

    // Alternative using native query for strings
    @Query(value = "SELECT * FROM users WHERE role IN :roles", nativeQuery = true)
    List<User> findByRoleStringIn(@Param("roles") List<String> roles);

    // Update unread notification count
    @Modifying
    @Query("UPDATE User u SET u.unreadNotifications = :count WHERE u.id = :userId")
    void updateUnreadNotificationCount(@Param("userId") Long userId, @Param("count") Integer count);
}