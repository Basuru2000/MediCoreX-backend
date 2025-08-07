package com.medicorex.repository;

import com.medicorex.entity.QuarantineActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuarantineActionLogRepository extends JpaRepository<QuarantineActionLog, Long> {

    List<QuarantineActionLog> findByQuarantineRecordIdOrderByPerformedAtDesc(Long quarantineRecordId);

    List<QuarantineActionLog> findByPerformedBy(String performedBy);
}