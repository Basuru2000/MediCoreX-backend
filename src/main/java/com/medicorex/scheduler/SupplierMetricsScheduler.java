package com.medicorex.scheduler;

import com.medicorex.service.supplier.SupplierMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierMetricsScheduler {

    private final SupplierMetricsService metricsService;

    /**
     * Calculate monthly metrics for all suppliers
     * Runs at 2 AM on the 1st of each month
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void calculateMonthlyMetrics() {
        log.info("Starting scheduled monthly metrics calculation");
        try {
            metricsService.calculateMonthlyMetrics();
            log.info("Completed scheduled monthly metrics calculation");
        } catch (Exception e) {
            log.error("Error in scheduled metrics calculation: {}", e.getMessage(), e);
        }
    }

    /**
     * Send metrics alerts for underperforming suppliers
     * Runs every Monday at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * MON")
    public void sendMetricsAlerts() {
        log.info("Checking for underperforming suppliers");
        // This can be extended to send notifications
        // Currently just logs the check
    }
}