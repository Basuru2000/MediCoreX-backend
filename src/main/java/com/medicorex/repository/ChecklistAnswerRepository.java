package com.medicorex.repository;

import com.medicorex.entity.ChecklistAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChecklistAnswerRepository extends JpaRepository<ChecklistAnswer, Long> {

    List<ChecklistAnswer> findByChecklistId(Long checklistId);

    Long countByChecklistIdAndIsCompliantTrue(Long checklistId);

    Long countByChecklistIdAndIsCompliantFalse(Long checklistId);
}