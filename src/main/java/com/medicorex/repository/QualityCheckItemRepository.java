package com.medicorex.repository;

import com.medicorex.entity.QualityCheckItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QualityCheckItemRepository extends JpaRepository<QualityCheckItem, Long> {

    List<QualityCheckItem> findByTemplateIdOrderByItemOrderAsc(Long templateId);

    Long countByTemplateId(Long templateId);
}