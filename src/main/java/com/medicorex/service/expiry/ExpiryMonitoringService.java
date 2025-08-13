package com.medicorex.service.expiry;

import com.medicorex.dto.AlertGenerationReportDTO;
import com.medicorex.dto.ExpiryCheckResultDTO;
import com.medicorex.entity.ExpiryCheckLog;
import com.medicorex.entity.ExpiryCheckLog.CheckStatus;
import com.medicorex.repository.ExpiryCheckLogRepository;
import com.medicorex.service.quarantine.QuarantineService;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryMonitoringService {

    private final ExpiryCheckLogRepository checkLogRepository;
    private final ExpiryAlertGenerator alertGenerator;
    private final BatchExpiryTrackingService batchExpiryTrackingService;
    private final NotificationService notificationService;

    @Autowired(required = false)
    private QuarantineService quarantineService;

    // Configuration to allow multiple manual checks per day (useful for testing)
    @Value("${expiry.check.allow-multiple-manual:true}")
    private boolean allowMultipleManualChecks;

    /**
     * Scheduled task to run daily at 2 AM
     * Cron expression: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void performDailyExpiryCheck() {
        log.info("Starting scheduled daily expiry check");

        // For scheduled checks, we always check if one has been completed today
        LocalDate today = LocalDate.now();
        if (checkLogRepository.hasCompletedCheckForDate(today)) {
            log.warn("Scheduled expiry check already completed for today, skipping");
            return;
        }

        executeExpiryCheck(today, "SCHEDULED");
    }

    /**
     * Auto-quarantine expired items (runs daily after expiry check)
     */
    @Scheduled(cron = "0 30 2 * * *") // Runs at 2:30 AM, after expiry check
    public void autoQuarantineExpiredItems() {
        log.info("Starting auto-quarantine process for expired items");

        if (quarantineService != null) {
            quarantineService.autoQuarantineExpiredBatches();
            log.info("Auto-quarantine process completed");
        } else {
            log.warn("QuarantineService not available - skipping auto-quarantine");
        }
    }

    /**
     * Manual trigger for expiry check
     */
    @Transactional
    public ExpiryCheckResultDTO performManualExpiryCheck() {
        log.info("Starting manual expiry check");
        LocalDate today = LocalDate.now();

        // Check if we should restrict manual checks
        if (!allowMultipleManualChecks) {
            // Check if already run today (any type)
            if (checkLogRepository.hasCompletedCheckForDate(today)) {
                log.warn("Expiry check already completed for today and multiple manual checks are disabled");

                // Instead of throwing exception, return the existing check result
                ExpiryCheckLog existingCheck = checkLogRepository.findByCheckDate(today)
                        .orElseThrow(() -> new IllegalStateException("Check record not found"));

                ExpiryCheckResultDTO result = buildCheckResultDTO(existingCheck);
                result.setErrorMessage("Expiry check already completed for today. Enable multiple manual checks in configuration to run again.");
                return result;
            }
        } else {
            // In development/testing mode, just log a warning about multiple checks
            long checksToday = checkLogRepository.countByCheckDateAndStatus(today, CheckStatus.COMPLETED);
            if (checksToday > 0) {
                log.info("Running additional manual check for today (check #{} for {})",
                        checksToday + 1, today);
            }
        }

        return executeExpiryCheck(today, "MANUAL");
    }

    /**
     * Force expiry check (admin only) - bypasses all restrictions
     */
    @Transactional
    public ExpiryCheckResultDTO forceExpiryCheck() {
        log.warn("FORCE expiry check initiated - bypassing all restrictions");
        return executeExpiryCheck(LocalDate.now(), "FORCE_MANUAL");
    }

    /**
     * Core expiry check execution logic
     */
    private ExpiryCheckResultDTO executeExpiryCheck(LocalDate checkDate, String triggeredBy) {
        LocalDateTime startTime = LocalDateTime.now();

        // Clean up any stale running checks first
        cleanupStaleChecks();

        ExpiryCheckLog checkLog = createCheckLog(checkDate, triggeredBy);

        try {
            // Generate alerts
            AlertGenerationReportDTO report = alertGenerator.generateAlertsForDate(checkDate, checkLog.getId());

            // Check batch expiry (THIS IS WHERE NOTIFICATIONS ARE CREATED)
            batchExpiryTrackingService.checkBatchExpiry(checkDate);

            // Update check log with results
            LocalDateTime endTime = LocalDateTime.now();
            checkLog.setEndTime(endTime);
            checkLog.setStatus(CheckStatus.COMPLETED);
            checkLog.setProductsChecked(report.getTotalProductsProcessed());
            checkLog.setAlertsGenerated(report.getTotalAlertsGenerated());
            checkLog.setExecutionTimeMs(ChronoUnit.MILLIS.between(startTime, endTime));

            checkLog = checkLogRepository.save(checkLog);

            log.info("Expiry check completed successfully. Type: {}, Products: {}, Alerts: {}, Time: {}ms",
                    triggeredBy,
                    report.getTotalProductsProcessed(),
                    report.getTotalAlertsGenerated(),
                    checkLog.getExecutionTimeMs());

            // OPTIONAL: Create summary notification for managers
            if (report.getTotalAlertsGenerated() > 0) {
                Map<String, String> params = new HashMap<>();
                params.put("count", String.valueOf(report.getTotalAlertsGenerated()));
                params.put("date", checkDate.toString());

                Map<String, Object> actionData = new HashMap<>();
                actionData.put("checkLogId", checkLog.getId());
                actionData.put("type", "expiry_check_summary");

                notificationService.notifyUsersByRole(
                        List.of("HOSPITAL_MANAGER"),
                        "SYSTEM_ANNOUNCEMENT",
                        params,
                        actionData
                );

                log.info("Summary notification sent for {} alerts generated",
                        report.getTotalAlertsGenerated());
            }

            return buildCheckResultDTO(checkLog, report);

        } catch (Exception e) {
            log.error("Expiry check failed", e);
            handleCheckFailure(checkLog, e);
            throw new RuntimeException("Expiry check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create initial check log entry
     */
    private ExpiryCheckLog createCheckLog(LocalDate checkDate, String triggeredBy) {
        ExpiryCheckLog checkLog = new ExpiryCheckLog();
        checkLog.setCheckDate(checkDate);
        checkLog.setStartTime(LocalDateTime.now());
        checkLog.setStatus(CheckStatus.RUNNING);
        checkLog.setCreatedBy(triggeredBy);

        return checkLogRepository.save(checkLog);
    }

    /**
     * Clean up checks that have been running for too long (>1 hour)
     */
    private void cleanupStaleChecks() {
        List<ExpiryCheckLog> runningChecks = checkLogRepository.findRunningChecks();
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        for (ExpiryCheckLog check : runningChecks) {
            if (check.getStartTime().isBefore(oneHourAgo)) {
                log.warn("Cleaning up stale check: {} started at {}",
                        check.getId(), check.getStartTime());
                check.setStatus(CheckStatus.FAILED);
                check.setEndTime(LocalDateTime.now());
                check.setErrorMessage("Check timed out after 1 hour");
                checkLogRepository.save(check);
            }
        }
    }

    /**
     * Handle check failure
     */
    private void handleCheckFailure(ExpiryCheckLog checkLog, Exception e) {
        checkLog.setStatus(CheckStatus.FAILED);
        checkLog.setEndTime(LocalDateTime.now());
        checkLog.setErrorMessage(e.getMessage());
        checkLog.setExecutionTimeMs(
                ChronoUnit.MILLIS.between(checkLog.getStartTime(), LocalDateTime.now())
        );
        checkLogRepository.save(checkLog);
    }

    /**
     * Get check history
     */
    @Transactional(readOnly = true)
    public List<ExpiryCheckResultDTO> getCheckHistory() {
        return checkLogRepository.findTop30ByOrderByCheckDateDesc().stream()
                .map(this::buildCheckResultDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get check status for a specific date
     */
    @Transactional(readOnly = true)
    public ExpiryCheckResultDTO getCheckStatus(LocalDate date) {
        return checkLogRepository.findByCheckDate(date)
                .map(this::buildCheckResultDTO)
                .orElse(null);
    }

    /**
     * Get today's check summary
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTodayCheckSummary() {
        LocalDate today = LocalDate.now();
        List<ExpiryCheckLog> todayChecks = checkLogRepository.findAllByCheckDateOrderByStartTimeDesc(today);

        Map<String, Object> summary = new HashMap<>();
        summary.put("date", today);
        summary.put("totalChecks", todayChecks.size());
        summary.put("scheduledChecks", todayChecks.stream()
                .filter(c -> "SCHEDULED".equals(c.getCreatedBy()))
                .count());
        summary.put("manualChecks", todayChecks.stream()
                .filter(c -> "MANUAL".equals(c.getCreatedBy()) || "FORCE_MANUAL".equals(c.getCreatedBy()))
                .count());
        summary.put("completedChecks", todayChecks.stream()
                .filter(c -> CheckStatus.COMPLETED.equals(c.getStatus()))
                .count());
        summary.put("failedChecks", todayChecks.stream()
                .filter(c -> CheckStatus.FAILED.equals(c.getStatus()))
                .count());
        summary.put("lastCheck", todayChecks.isEmpty() ? null : buildCheckResultDTO(todayChecks.get(0)));

        return summary;
    }

    /**
     * Build result DTO from check log
     */
    private ExpiryCheckResultDTO buildCheckResultDTO(ExpiryCheckLog checkLog) {
        return ExpiryCheckResultDTO.builder()
                .checkLogId(checkLog.getId())
                .checkDate(checkLog.getCheckDate())
                .startTime(checkLog.getStartTime())
                .endTime(checkLog.getEndTime())
                .status(checkLog.getStatus().toString())
                .productsChecked(checkLog.getProductsChecked())
                .alertsGenerated(checkLog.getAlertsGenerated())
                .executionTimeMs(checkLog.getExecutionTimeMs())
                .errorMessage(checkLog.getErrorMessage())
                .build();
    }

    /**
     * Build result DTO with additional report data
     */
    private ExpiryCheckResultDTO buildCheckResultDTO(ExpiryCheckLog checkLog, AlertGenerationReportDTO report) {
        ExpiryCheckResultDTO dto = buildCheckResultDTO(checkLog);

        // Add breakdown data if available
        if (report != null && report.getProductAlerts() != null) {
            // Group by severity
            Map<String, Integer> alertsBySeverity = new HashMap<>();
            Map<String, Integer> alertsByDaysRange = new HashMap<>();

            report.getProductAlerts().forEach(alert -> {
                // Count by severity
                alertsBySeverity.merge(alert.getSeverity(), 1, Integer::sum);

                // Count by days range
                String range = getDaysRange(alert.getDaysUntilExpiry());
                alertsByDaysRange.merge(range, 1, Integer::sum);
            });

            dto.setAlertsBySeverity(alertsBySeverity);
            dto.setAlertsByDaysRange(alertsByDaysRange);
        }

        return dto;
    }

    /**
     * Categorize days until expiry into ranges
     */
    private String getDaysRange(Integer days) {
        if (days <= 7) return "0-7 days";
        else if (days <= 30) return "8-30 days";
        else if (days <= 60) return "31-60 days";
        else if (days <= 90) return "61-90 days";
        else return "91+ days";
    }
}