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

    // ✅ FIXED: Simply get users by role without 'enabled' check
    List<User> findByRoleIn(List<String> roles);

    // ✅ REMOVE or COMMENT OUT this problematic method
    // @Query("SELECT u FROM User u WHERE u.enabled = true AND u.role IN :roles")
    // List<User> findActiveUsersByRoleIn(@Param("roles") List<String> roles);
}