package com.medicorex.repository;

import com.medicorex.entity.QualityChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QualityChecklistTemplateRepository extends JpaRepository<QualityChecklistTemplate, Long> {

    List<QualityChecklistTemplate> findByIsActiveTrue();

    Optional<QualityChecklistTemplate> findByIsDefaultTrue();

    List<QualityChecklistTemplate> findByCategory(String category);

    @Query("SELECT t FROM QualityChecklistTemplate t LEFT JOIN FETCH t.items WHERE t.id = :id")
    Optional<QualityChecklistTemplate> findByIdWithItems(Long id);

    @Query("SELECT t FROM QualityChecklistTemplate t LEFT JOIN FETCH t.items WHERE t.isActive = true")
    List<QualityChecklistTemplate> findActiveTemplatesWithItems();
}