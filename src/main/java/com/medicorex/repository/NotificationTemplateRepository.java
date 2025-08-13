package com.medicorex.repository;

import com.medicorex.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByCodeAndActive(String code, Boolean active);

    Optional<NotificationTemplate> findByCode(String code);

    List<NotificationTemplate> findByCategory(String category);

    List<NotificationTemplate> findByActive(Boolean active);

    boolean existsByCode(String code);
}