package com.medicorex.service.quarantine;

import com.medicorex.entity.QuarantineActionLog;
import com.medicorex.repository.QuarantineActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuarantineWorkflowService {

    private final QuarantineActionLogRepository actionLogRepository;

    /**
     * Log quarantine action for audit trail
     */
    public void logAction(Long quarantineRecordId, String action, String performedBy,
                          String previousStatus, String newStatus, String comments) {
        QuarantineActionLog actionLog = new QuarantineActionLog();  // ✅ FIXED: Renamed from 'log' to 'actionLog'
        actionLog.setQuarantineRecordId(quarantineRecordId);
        actionLog.setAction(action);
        actionLog.setPerformedBy(performedBy);
        actionLog.setPerformedAt(LocalDateTime.now());
        actionLog.setPreviousStatus(previousStatus);
        actionLog.setNewStatus(newStatus);
        actionLog.setComments(comments);

        actionLogRepository.save(actionLog);

        log.info("Logged action: {} for quarantine record {} by {}",
                action, quarantineRecordId, performedBy);  // ✅ FIXED: Now 'log' refers to Slf4j logger
    }

    /**
     * Get action history for a quarantine record
     */
    @Transactional(readOnly = true)
    public List<QuarantineActionLog> getActionHistory(Long quarantineRecordId) {
        return actionLogRepository.findByQuarantineRecordIdOrderByPerformedAtDesc(
                quarantineRecordId);
    }

    /**
     * Validate workflow transition
     */
    public boolean isValidTransition(String currentStatus, String targetAction) {
        switch (currentStatus) {
            case "PENDING_REVIEW":
                return "REVIEW".equals(targetAction);
            case "UNDER_REVIEW":
                return "APPROVE_DISPOSAL".equals(targetAction) ||
                        "APPROVE_RETURN".equals(targetAction);
            case "APPROVED_FOR_DISPOSAL":
                return "DISPOSE".equals(targetAction);
            case "APPROVED_FOR_RETURN":
                return "RETURN".equals(targetAction);
            default:
                return false;
        }
    }
}