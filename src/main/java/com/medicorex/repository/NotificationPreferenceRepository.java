package com.medicorex.repository;

import com.medicorex.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("SELECT np FROM NotificationPreference np WHERE np.digestEnabled = true AND np.digestTime = :time")
    List<NotificationPreference> findUsersForDigest(@Param("time") LocalTime time);

    @Query("SELECT np FROM NotificationPreference np WHERE np.emailEnabled = true")
    List<NotificationPreference> findUsersWithEmailEnabled();

    @Query("SELECT np FROM NotificationPreference np WHERE np.smsEnabled = true")
    List<NotificationPreference> findUsersWithSmsEnabled();

    @Query("SELECT COUNT(np) FROM NotificationPreference np WHERE np.user.id = :userId AND np.inAppEnabled = true")
    Long countEnabledPreferences(@Param("userId") Long userId);
}