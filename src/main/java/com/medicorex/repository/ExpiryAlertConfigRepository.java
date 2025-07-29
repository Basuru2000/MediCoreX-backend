package com.medicorex.repository;

import com.medicorex.entity.ExpiryAlertConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpiryAlertConfigRepository extends JpaRepository<ExpiryAlertConfig, Long> {

    List<ExpiryAlertConfig> findByActiveTrue();

    List<ExpiryAlertConfig> findByActiveTrueOrderBySortOrderAsc();

    Optional<ExpiryAlertConfig> findByDaysBeforeExpiry(Integer days);

    boolean existsByDaysBeforeExpiry(Integer days);

    @Query("SELECT c FROM ExpiryAlertConfig c WHERE c.active = true AND c.notifyRoles LIKE %:role%")
    List<ExpiryAlertConfig> findActiveConfigsForRole(String role);

    @Query("SELECT MAX(c.sortOrder) FROM ExpiryAlertConfig c")
    Integer findMaxSortOrder();
}