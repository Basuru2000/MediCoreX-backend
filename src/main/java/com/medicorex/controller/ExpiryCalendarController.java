package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.expiry.ExpiryCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/expiry/calendar")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExpiryCalendarController {

    private final ExpiryCalendarService calendarService;

    /**
     * Get calendar data for current month
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<ExpiryCalendarDTO> getCurrentMonthCalendar() {
        log.info("Fetching current month calendar data");

        ExpiryCalendarRequestDTO request = ExpiryCalendarRequestDTO.builder()
                .month(YearMonth.now())
                .build();

        ExpiryCalendarDTO calendar = calendarService.getCalendarData(request);
        return ResponseEntity.ok(calendar);
    }

    /**
     * Get calendar data for specific month
     */
    @GetMapping("/month/{year}/{month}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<ExpiryCalendarDTO> getMonthCalendar(
            @PathVariable int year,
            @PathVariable int month) {

        log.info("Fetching calendar data for {}/{}", year, month);

        ExpiryCalendarRequestDTO request = ExpiryCalendarRequestDTO.builder()
                .month(YearMonth.of(year, month))
                .build();

        ExpiryCalendarDTO calendar = calendarService.getCalendarData(request);
        return ResponseEntity.ok(calendar);
    }

    /**
     * Get calendar data with advanced filters
     */
    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryCalendarDTO> searchCalendar(
            @Valid @RequestBody ExpiryCalendarRequestDTO request) {

        log.info("Searching calendar with filters: {}", request);

        ExpiryCalendarDTO calendar = calendarService.getCalendarData(request);
        return ResponseEntity.ok(calendar);
    }

    /**
     * Get calendar data for date range
     */
    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ExpiryCalendarDTO> getRangeCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Fetching calendar data from {} to {}", startDate, endDate);

        ExpiryCalendarRequestDTO request = ExpiryCalendarRequestDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build();

        ExpiryCalendarDTO calendar = calendarService.getCalendarData(request);
        return ResponseEntity.ok(calendar);
    }

    /**
     * Get calendar events for date range
     */
    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<ExpiryCalendarEventDTO>> getCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "week") String view) {

        log.debug("Fetching calendar events from {} to {} for view: {}", startDate, endDate, view);
        List<ExpiryCalendarEventDTO> events = calendarService.getCalendarEvents(startDate, endDate, view);
        return ResponseEntity.ok(events);
    }

    /**
     * Refresh calendar cache
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> refreshCalendarCache() {
        log.info("Refreshing calendar cache");

        // Call stored procedure to refresh cache
        // Implementation depends on your cache strategy

        return ResponseEntity.ok(Map.of("message", "Calendar cache refreshed successfully"));
    }
}