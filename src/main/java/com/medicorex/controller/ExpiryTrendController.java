package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.exception.BusinessException;
import com.medicorex.service.ExpiryTrendService;
import com.medicorex.service.ExpiryPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/expiry/trends")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExpiryTrendController {

    private final ExpiryTrendService trendService;
    private final ExpiryPredictionService predictionService;

    /**
     * Get expiry trend analysis for specified date range
     */
    @GetMapping("/analysis")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<TrendAnalysisResponseDTO> getTrendAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAILY") String granularity) {

        log.info("Fetching trend analysis from {} to {} with {} granularity",
                startDate, endDate, granularity);

        TrendAnalysisResponseDTO analysis = trendService.analyzeTrends(startDate, endDate, granularity);
        return ResponseEntity.ok(analysis);
    }

    /**
     * Advanced trend analysis with request DTO
     */
    @PostMapping("/analysis/advanced")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<TrendAnalysisResponseDTO> getAdvancedTrendAnalysis(
            @Valid @RequestBody ExpiryTrendRequestDTO request) {

        log.info("Advanced trend analysis requested with parameters: {}", request);

        // Validate date range
        if (!request.isValidDateRange()) {
            throw new BusinessException("Invalid date range: start date must be before end date");
        }

        // Use optimal granularity if not specified
        String granularity = request.getGranularity() != null ?
                request.getGranularity() : request.getOptimalGranularity();

        TrendAnalysisResponseDTO analysis;

        // Check if this is a category-specific request
        if (request.isCategorySpecific()) {
            analysis = trendService.analyzeCategoryTrends(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getCategoryId(),
                    granularity
            );
        } else {
            analysis = trendService.analyzeTrends(
                    request.getStartDate(),
                    request.getEndDate(),
                    granularity
            );
        }

        // Apply filters if present
        if (request.hasFilters()) {
            analysis = trendService.applyFilters(analysis, request);
        }

        // Add predictions if requested
        if (request.getIncludePredictions()) {
            ExpiryPredictionDTO predictions = predictionService.generatePredictions(
                    request.getDaysAhead()
            );
            analysis.setPredictions(predictions);
        }

        return ResponseEntity.ok(analysis);
    }

    /**
     * Get current trend metrics
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<ExpiryTrendDTO> getCurrentTrends() {
        ExpiryTrendDTO trends = trendService.getCurrentTrends();
        return ResponseEntity.ok(trends);
    }

    /**
     * Get historical trend data points
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ExpiryTrendDataPointDTO>> getHistoricalTrends(
            @RequestParam(defaultValue = "30") int daysBack) {

        List<ExpiryTrendDataPointDTO> history = trendService.getHistoricalTrends(daysBack);
        return ResponseEntity.ok(history);
    }

    /**
     * Get predictive analysis
     */
    @GetMapping("/predictions")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<ExpiryPredictionDTO> getPredictions(
            @RequestParam(defaultValue = "30") int daysAhead) {

        ExpiryPredictionDTO predictions = predictionService.generatePredictions(daysAhead);
        return ResponseEntity.ok(predictions);
    }

    /**
     * Get category-wise trend analysis
     */
    @GetMapping("/by-category")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, List<ExpiryTrendDataPointDTO>>> getCategoryTrends(
            @RequestParam(defaultValue = "30") int daysBack) {

        Map<String, List<ExpiryTrendDataPointDTO>> categoryTrends =
                trendService.getCategoryWiseTrends(daysBack);
        return ResponseEntity.ok(categoryTrends);
    }

    /**
     * Export trend report as CSV
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<byte[]> exportTrendReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        byte[] csvData = trendService.exportTrendReport(startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment",
                String.format("expiry_trends_%s_to_%s.csv", startDate, endDate));

        return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
    }

    /**
     * Trigger manual trend snapshot
     */
    @PostMapping("/snapshot")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Map<String, String>> createSnapshot() {
        trendService.captureSnapshot();
        return ResponseEntity.ok(Map.of("message", "Trend snapshot created successfully"));
    }

    /**
     * Get trend comparison between periods
     */
    @GetMapping("/compare")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, Object>> comparePeriods(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1End,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2End) {

        Map<String, Object> comparison = trendService.comparePeriods(
                period1Start, period1End, period2Start, period2End);
        return ResponseEntity.ok(comparison);
    }
}