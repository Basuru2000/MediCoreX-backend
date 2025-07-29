package com.medicorex.service.expiry;

import com.medicorex.dto.AlertGenerationReportDTO;
import com.medicorex.dto.ExpiryCheckResultDTO;
import com.medicorex.entity.ExpiryCheckLog;
import com.medicorex.entity.ExpiryCheckLog.CheckStatus;
import com.medicorex.repository.ExpiryCheckLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Scheduled task to run daily at 2 AM
     * Cron expression: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void performDailyExpiryCheck() {
        log.info("Starting scheduled daily expiry check");
        executeExpiryCheck(LocalDate.now(), "SCHEDULED");
    }

    /**
     * Manual trigger for expiry check
     */
    @Transactional
    public ExpiryCheckResultDTO performManualExpiryCheck() {
        log.info("Starting manual expiry check");
        LocalDate today = LocalDate.now();

        // Check if already run today
        if (checkLogRepository.hasCompletedCheckForDate(today)) {
            log.warn("Expiry check already completed for today");
            throw new IllegalStateException("Expiry check has already been completed for today");
        }

        return executeExpiryCheck(today, "MANUAL");
    }

    /**
     * Core expiry check execution logic
     */
    private ExpiryCheckResultDTO executeExpiryCheck(LocalDate checkDate, String triggeredBy) {
        LocalDateTime startTime = LocalDateTime.now();
        ExpiryCheckLog checkLog = createCheckLog(checkDate, triggeredBy);

        try {
            // Clean up any stale running checks
            cleanupStaleChecks();

            // Generate alerts
            AlertGenerationReportDTO report = alertGenerator.generateAlertsForDate(checkDate, checkLog.getId());

            // Update check log with results
            LocalDateTime endTime = LocalDateTime.now();
            checkLog.setEndTime(endTime);
            checkLog.setStatus(CheckStatus.COMPLETED);
            checkLog.setProductsChecked(report.getTotalProductsProcessed());
            checkLog.setAlertsGenerated(report.getTotalAlertsGenerated());
            checkLog.setExecutionTimeMs(ChronoUnit.MILLIS.between(startTime, endTime));

            checkLog = checkLogRepository.save(checkLog);

            log.info("Expiry check completed successfully. Products: {}, Alerts: {}, Time: {}ms",
                    report.getTotalProductsProcessed(),
                    report.getTotalAlertsGenerated(),
                    checkLog.getExecutionTimeMs());

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
                log.warn("Cleaning up stale check: {}", check.getId());
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