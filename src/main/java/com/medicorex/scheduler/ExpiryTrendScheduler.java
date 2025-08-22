package com.medicorex.scheduler;

import com.medicorex.service.ExpiryTrendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiryTrendScheduler {

    private final ExpiryTrendService trendService;

    /**
     * Capture daily trend snapshot at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void captureDailySnapshot() {
        log.info("Starting daily trend snapshot capture");
        try {
            trendService.captureSnapshot();
            log.info("Daily trend snapshot captured successfully");
        } catch (Exception e) {
            log.error("Error capturing daily trend snapshot", e);
        }
    }
}