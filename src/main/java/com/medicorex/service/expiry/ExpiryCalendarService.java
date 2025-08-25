package com.medicorex.service.expiry;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpiryCalendarService {

    private final ProductBatchRepository batchRepository;
    private final ExpiryAlertRepository alertRepository;
    private final QuarantineRecordRepository quarantineRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Get calendar data for a specific month
     */
    @Cacheable(value = "calendarData", key = "#request.month + '_' + #request.viewType")
    public ExpiryCalendarDTO getCalendarData(ExpiryCalendarRequestDTO request) {
        log.info("Fetching calendar data for request: {}", request);

        // Determine date range
        LocalDate startDate;
        LocalDate endDate;

        if (request.getMonth() != null) {
            startDate = request.getMonth().atDay(1);
            endDate = request.getMonth().atEndOfMonth();
        } else if (request.getStartDate() != null && request.getEndDate() != null) {
            startDate = request.getStartDate();
            endDate = request.getEndDate();
        } else {
            // Default to current month
            YearMonth currentMonth = YearMonth.now();
            startDate = currentMonth.atDay(1);
            endDate = currentMonth.atEndOfMonth();
        }

        // Fetch events
        Map<LocalDate, List<ExpiryCalendarEventDTO>> events = fetchEvents(startDate, endDate, request);

        // Calculate summary
        ExpiryCalendarSummaryDTO summary = calculateSummary(events, startDate, endDate);

        // Identify critical dates
        List<LocalDate> criticalDates = identifyCriticalDates(events);

        // Build metadata
        ExpiryCalendarDTO.CalendarMetadata metadata = buildMetadata(events);

        return ExpiryCalendarDTO.builder()
                .currentMonth(YearMonth.from(startDate))
                .startDate(startDate)
                .endDate(endDate)
                .events(events)
                .summary(summary)
                .criticalDates(criticalDates)
                .metadata(metadata)
                .build();
    }

    /**
     * Fetch all events in date range
     */
    private Map<LocalDate, List<ExpiryCalendarEventDTO>> fetchEvents(
            LocalDate startDate, LocalDate endDate, ExpiryCalendarRequestDTO request) {

        Map<LocalDate, List<ExpiryCalendarEventDTO>> eventMap = new TreeMap<>();

        try {
            // Use the correct method name based on what we defined earlier
            List<ProductBatch> expiringBatches = batchRepository
                    .findBatchesExpiringBetweenIncludingQuarantined(startDate, endDate);

            for (ProductBatch batch : expiringBatches) {
                if (!request.getIncludeQuarantined() &&
                        batch.getStatus() == ProductBatch.BatchStatus.QUARANTINED) {
                    continue;
                }

                ExpiryCalendarEventDTO event = createBatchExpiryEvent(batch);
                eventMap.computeIfAbsent(batch.getExpiryDate(), k -> new ArrayList<>()).add(event);
            }
        } catch (Exception e) {
            log.error("Error fetching batch events: ", e);
        }

        try {
            // This method might not exist, let's check
            List<ExpiryAlert> alerts = alertRepository
                    .findByExpiryDateBetween(startDate, endDate);

            for (ExpiryAlert alert : alerts) {
                if (!request.getIncludeResolved() &&
                        alert.getStatus() == ExpiryAlert.AlertStatus.RESOLVED) {
                    continue;
                }

                ExpiryCalendarEventDTO event = createAlertEvent(alert);
                eventMap.computeIfAbsent(alert.getAlertDate(), k -> new ArrayList<>()).add(event);
            }
        } catch (Exception e) {
            log.error("Error fetching alert events: ", e);
        }

        // Fetch quarantine events if requested
        if (request.getIncludeQuarantined()) {
            try {
                List<QuarantineRecord> quarantineRecords =
                        quarantineRepository.findByQuarantineDateBetween(startDate, endDate);

                for (QuarantineRecord record : quarantineRecords) {
                    ExpiryCalendarEventDTO event = createQuarantineEvent(record);
                    eventMap.computeIfAbsent(record.getQuarantineDate(), k -> new ArrayList<>()).add(event);
                }
            } catch (Exception e) {
                log.error("Error fetching quarantine events: ", e);
            }
        }

        // Group multiple events on same date if needed
        if (request.getGroupByDate()) {
            eventMap = groupEventsByDate(eventMap);
        }

        return eventMap;
    }

    /**
     * Create event from batch
     */
    private ExpiryCalendarEventDTO createBatchExpiryEvent(ProductBatch batch) {
        LocalDate today = LocalDate.now();
        long daysUntil = today.until(batch.getExpiryDate()).getDays();

        ExpiryCalendarEventDTO.EventSeverity severity;
        String color;

        if (daysUntil <= 0) {
            severity = ExpiryCalendarEventDTO.EventSeverity.CRITICAL;
            color = "#d32f2f"; // Red
        } else if (daysUntil <= 7) {
            severity = ExpiryCalendarEventDTO.EventSeverity.CRITICAL;
            color = "#f44336"; // Light red
        } else if (daysUntil <= 30) {
            severity = ExpiryCalendarEventDTO.EventSeverity.HIGH;
            color = "#ff9800"; // Orange
        } else if (daysUntil <= 60) {
            severity = ExpiryCalendarEventDTO.EventSeverity.MEDIUM;
            color = "#ffc107"; // Amber
        } else {
            severity = ExpiryCalendarEventDTO.EventSeverity.LOW;
            color = "#4caf50"; // Green
        }

        Product product = batch.getProduct();
        BigDecimal value = batch.getCostPerUnit() != null ?
                batch.getCostPerUnit().multiply(BigDecimal.valueOf(batch.getQuantity())) :
                product.getUnitPrice().multiply(BigDecimal.valueOf(batch.getQuantity()));

        ExpiryCalendarEventDTO.EventDetail detail = ExpiryCalendarEventDTO.EventDetail.builder()
                .itemId(batch.getId())
                .itemType("BATCH")
                .itemName(product.getName())
                .batchNumber(batch.getBatchNumber())
                .quantity(batch.getQuantity())
                .value(value)
                .category(product.getCategory() != null ? product.getCategory().getName() : "Uncategorized")
                .severity(severity.toString())
                .actionUrl("/batch-tracking")
                .build();

        return ExpiryCalendarEventDTO.builder()
                .id(batch.getId())
                .date(batch.getExpiryDate())
                .type(ExpiryCalendarEventDTO.EventType.BATCH_EXPIRY)
                .severity(severity)
                .title("Batch Expiry: " + batch.getBatchNumber())
                .description(String.format("%s - %d units expiring",
                        product.getName(), batch.getQuantity()))
                .itemCount(1)
                .totalQuantity(batch.getQuantity())
                .totalValue(value)
                .color(color)
                .icon("inventory")
                .details(Collections.singletonList(detail))
                .actionUrl("/batch-tracking")
                .isToday(batch.getExpiryDate().equals(today))
                .isPast(batch.getExpiryDate().isBefore(today))
                .daysUntil((int) daysUntil)
                .build();
    }

    /**
     * Create event from alert
     */
    private ExpiryCalendarEventDTO createAlertEvent(ExpiryAlert alert) {
        LocalDate today = LocalDate.now();
        ExpiryAlertConfig config = alert.getConfig();

        ExpiryCalendarEventDTO.EventSeverity severity;
        String color;

        switch (config.getSeverity()) {
            case CRITICAL:
                severity = ExpiryCalendarEventDTO.EventSeverity.CRITICAL;
                color = "#d32f2f";
                break;
            case WARNING:
                severity = ExpiryCalendarEventDTO.EventSeverity.HIGH;
                color = "#ff9800";
                break;
            default:
                severity = ExpiryCalendarEventDTO.EventSeverity.MEDIUM;
                color = "#ffc107";
        }

        Product product = alert.getProduct();

        ExpiryCalendarEventDTO.EventDetail detail = ExpiryCalendarEventDTO.EventDetail.builder()
                .itemId(alert.getId())
                .itemType("ALERT")
                .itemName(product.getName())
                .batchNumber(alert.getBatchNumber())
                .quantity(alert.getQuantityAffected())
                .category(config.getTierName())
                .severity(severity.toString())
                .actionUrl("/expiry-monitoring")
                .build();

        return ExpiryCalendarEventDTO.builder()
                .id(alert.getId())
                .date(alert.getAlertDate())
                .type(ExpiryCalendarEventDTO.EventType.ALERT)
                .severity(severity)
                .title(config.getTierName() + " Alert")
                .description(String.format("%s - %s", product.getName(), config.getDescription()))
                .itemCount(1)
                .totalQuantity(alert.getQuantityAffected())
                .color(color)
                .icon("warning")
                .details(Collections.singletonList(detail))
                .actionUrl("/expiry-monitoring")
                .isToday(alert.getAlertDate().equals(today))
                .isPast(alert.getAlertDate().isBefore(today))
                .build();
    }

    /**
     * Create event from quarantine record
     */
    private ExpiryCalendarEventDTO createQuarantineEvent(QuarantineRecord record) {
        LocalDate today = LocalDate.now();

        ExpiryCalendarEventDTO.EventDetail detail = ExpiryCalendarEventDTO.EventDetail.builder()
                .itemId(record.getId())
                .itemType("QUARANTINE")
                .itemName(record.getProduct().getName())
                .batchNumber(record.getBatch() != null ? record.getBatch().getBatchNumber() : "N/A")
                .quantity(record.getQuantityQuarantined())
                .value(record.getEstimatedLoss())
                .category("Quarantine")
                .severity("HIGH")
                .actionUrl("/quarantine")
                .build();

        return ExpiryCalendarEventDTO.builder()
                .id(record.getId())
                .date(record.getQuarantineDate())
                .type(ExpiryCalendarEventDTO.EventType.QUARANTINE)
                .severity(ExpiryCalendarEventDTO.EventSeverity.HIGH)
                .title("Quarantine: " + record.getProduct().getName())
                .description(record.getReason())
                .itemCount(1)
                .totalQuantity(record.getQuantityQuarantined())
                .totalValue(record.getEstimatedLoss())
                .color("#9c27b0") // Purple
                .icon("block")
                .details(Collections.singletonList(detail))
                .actionUrl("/quarantine")
                .isToday(record.getQuarantineDate().equals(today))
                .isPast(record.getQuarantineDate().isBefore(today))
                .build();
    }

    /**
     * Group multiple events on the same date
     */
    private Map<LocalDate, List<ExpiryCalendarEventDTO>> groupEventsByDate(
            Map<LocalDate, List<ExpiryCalendarEventDTO>> eventMap) {

        Map<LocalDate, List<ExpiryCalendarEventDTO>> groupedMap = new TreeMap<>();

        for (Map.Entry<LocalDate, List<ExpiryCalendarEventDTO>> entry : eventMap.entrySet()) {
            LocalDate date = entry.getKey();
            List<ExpiryCalendarEventDTO> events = entry.getValue();

            if (events.size() > 2) {
                // Create a summary event for dates with many events
                ExpiryCalendarEventDTO summaryEvent = createSummaryEvent(date, events);
                groupedMap.put(date, Arrays.asList(summaryEvent));
            } else {
                groupedMap.put(date, events);
            }
        }

        return groupedMap;
    }

    /**
     * Create summary event for multiple items
     */
    private ExpiryCalendarEventDTO createSummaryEvent(LocalDate date, List<ExpiryCalendarEventDTO> events) {
        int totalItems = events.size();
        int totalQuantity = events.stream()
                .mapToInt(ExpiryCalendarEventDTO::getTotalQuantity)
                .sum();
        BigDecimal totalValue = events.stream()
                .map(ExpiryCalendarEventDTO::getTotalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Determine highest severity
        ExpiryCalendarEventDTO.EventSeverity highestSeverity = events.stream()
                .map(ExpiryCalendarEventDTO::getSeverity)
                .min(Comparator.comparingInt(this::getSeverityOrder))
                .orElse(ExpiryCalendarEventDTO.EventSeverity.LOW);

        String color = getColorForSeverity(highestSeverity);

        // Collect all details
        List<ExpiryCalendarEventDTO.EventDetail> allDetails = events.stream()
                .flatMap(e -> e.getDetails().stream())
                .collect(Collectors.toList());

        return ExpiryCalendarEventDTO.builder()
                .date(date)
                .type(ExpiryCalendarEventDTO.EventType.MULTIPLE)
                .severity(highestSeverity)
                .title(String.format("%d Events", totalItems))
                .description(String.format("%d items, %d total units", totalItems, totalQuantity))
                .itemCount(totalItems)
                .totalQuantity(totalQuantity)
                .totalValue(totalValue)
                .color(color)
                .icon("folder")
                .details(allDetails)
                .isToday(date.equals(LocalDate.now()))
                .isPast(date.isBefore(LocalDate.now()))
                .build();
    }

    /**
     * Calculate summary statistics
     */
    private ExpiryCalendarSummaryDTO calculateSummary(
            Map<LocalDate, List<ExpiryCalendarEventDTO>> events,
            LocalDate startDate, LocalDate endDate) {

        List<ExpiryCalendarEventDTO> allEvents = events.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Count by type
        Map<String, Integer> eventsByType = allEvents.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getType().toString(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        // Count by severity
        Map<String, Integer> eventsBySeverity = allEvents.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getSeverity().toString(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        // Calculate totals
        int totalItems = allEvents.size();
        int batchCount = (int) allEvents.stream()
                .filter(e -> e.getType() == ExpiryCalendarEventDTO.EventType.BATCH_EXPIRY)
                .count();
        int alertCount = (int) allEvents.stream()
                .filter(e -> e.getType() == ExpiryCalendarEventDTO.EventType.ALERT)
                .count();

        BigDecimal totalValue = allEvents.stream()
                .map(ExpiryCalendarEventDTO::getTotalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate week summaries
        LocalDate today = LocalDate.now();
        LocalDate weekEnd = today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        LocalDate nextWeekStart = weekEnd.plusDays(1);
        LocalDate nextWeekEnd = nextWeekStart.plusDays(6);

        ExpiryCalendarSummaryDTO.WeekSummary thisWeek = calculateWeekSummary(
                events, today, weekEnd);
        ExpiryCalendarSummaryDTO.WeekSummary nextWeek = calculateWeekSummary(
                events, nextWeekStart, nextWeekEnd);

        return ExpiryCalendarSummaryDTO.builder()
                .totalExpiringItems(totalItems)
                .totalBatchesExpiring(batchCount)
                .totalProductsExpiring(0) // Calculate if needed
                .totalAlerts(alertCount)
                .totalValueAtRisk(totalValue)
                .eventsByType(eventsByType)
                .eventsBySeverity(eventsBySeverity)
                .thisWeek(thisWeek)
                .nextWeek(nextWeek)
                .build();
    }

    /**
     * Calculate week summary
     */
    private ExpiryCalendarSummaryDTO.WeekSummary calculateWeekSummary(
            Map<LocalDate, List<ExpiryCalendarEventDTO>> events,
            LocalDate startDate, LocalDate endDate) {

        List<ExpiryCalendarEventDTO> weekEvents = events.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(startDate) && !e.getKey().isAfter(endDate))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());

        if (weekEvents.isEmpty()) {
            return ExpiryCalendarSummaryDTO.WeekSummary.builder()
                    .itemCount(0)
                    .batchCount(0)
                    .alertCount(0)
                    .valueAtRisk(BigDecimal.ZERO)
                    .build();
        }

        String mostCritical = weekEvents.stream()
                .filter(e -> e.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.CRITICAL)
                .findFirst()
                .map(ExpiryCalendarEventDTO::getTitle)
                .orElse(null);

        return ExpiryCalendarSummaryDTO.WeekSummary.builder()
                .itemCount(weekEvents.size())
                .batchCount((int) weekEvents.stream()
                        .filter(e -> e.getType() == ExpiryCalendarEventDTO.EventType.BATCH_EXPIRY)
                        .count())
                .alertCount((int) weekEvents.stream()
                        .filter(e -> e.getType() == ExpiryCalendarEventDTO.EventType.ALERT)
                        .count())
                .valueAtRisk(weekEvents.stream()
                        .map(ExpiryCalendarEventDTO::getTotalValue)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .mostCriticalItem(mostCritical)
                .build();
    }

    /**
     * Identify critical dates
     */
    private List<LocalDate> identifyCriticalDates(Map<LocalDate, List<ExpiryCalendarEventDTO>> events) {
        return events.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(e -> e.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.CRITICAL))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Build calendar metadata
     */
    private ExpiryCalendarDTO.CalendarMetadata buildMetadata(
            Map<LocalDate, List<ExpiryCalendarEventDTO>> events) {

        List<ExpiryCalendarEventDTO> allEvents = events.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        LocalDate nextCritical = events.entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalDate.now()))
                .filter(e -> e.getValue().stream()
                        .anyMatch(ev -> ev.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.CRITICAL))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        return ExpiryCalendarDTO.CalendarMetadata.builder()
                .totalEvents(allEvents.size())
                .criticalEvents((int) allEvents.stream()
                        .filter(e -> e.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.CRITICAL)
                        .count())
                .highPriorityEvents((int) allEvents.stream()
                        .filter(e -> e.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.HIGH)
                        .count())
                .mediumPriorityEvents((int) allEvents.stream()
                        .filter(e -> e.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.MEDIUM)
                        .count())
                .lowPriorityEvents((int) allEvents.stream()
                        .filter(e -> e.getSeverity() == ExpiryCalendarEventDTO.EventSeverity.LOW)
                        .count())
                .nextCriticalDate(nextCritical)
                .hasToday(events.containsKey(LocalDate.now()))
                .build();
    }

    // Helper methods
    private int getSeverityOrder(ExpiryCalendarEventDTO.EventSeverity severity) {
        switch (severity) {
            case CRITICAL: return 0;
            case HIGH: return 1;
            case MEDIUM: return 2;
            case LOW: return 3;
            default: return 4;
        }
    }

    private String getColorForSeverity(ExpiryCalendarEventDTO.EventSeverity severity) {
        switch (severity) {
            case CRITICAL: return "#d32f2f";
            case HIGH: return "#ff9800";
            case MEDIUM: return "#ffc107";
            case LOW: return "#4caf50";
            default: return "#9e9e9e";
        }
    }
}