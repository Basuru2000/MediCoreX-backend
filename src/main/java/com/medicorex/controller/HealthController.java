package com.medicorex.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @Value("${spring.application.name:MediCoreX}")
    private String applicationName;

    @Value("${app.version:1.0.0}")
    private String applicationVersion;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("application", applicationName);
        response.put("version", applicationVersion);
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "Welcome to MediCoreX Backend API");

        // API documentation
        response.put("api", Map.of(
                "documentation", "API documentation available at /api-docs",
                "health", "/health",
                "endpoints", Map.of(
                        "authentication", Map.of(
                                "login", "POST /api/auth/login",
                                "register", "POST /api/auth/register"
                        ),
                        "test", Map.of(
                                "hello", "GET /api/test/hello",
                                "health", "GET /api/test/health"
                        ),
                        "resources", Map.of(
                                "users", "/api/users (requires HOSPITAL_MANAGER role)",
                                "products", "/api/products (requires authentication)",
                                "categories", "/api/categories (requires authentication)",
                                "stock", "/api/stock (requires authentication)"
                        )
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();
        HttpStatus status = HttpStatus.OK;

        // Database health check
        components.put("database", checkDatabase());

        // Memory health check
        components.put("memory", checkMemory());

        // Disk space check
        components.put("diskSpace", checkDiskSpace());

        // Determine overall health
        boolean isHealthy = components.values().stream()
                .allMatch(component -> "UP".equals(((Map<?, ?>) component).get("status")));

        if (!isHealthy) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        }

        response.put("status", isHealthy ? "UP" : "DOWN");
        response.put("components", components);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/health/liveness")
    public ResponseEntity<Map<String, Object>> liveness() {
        // Simple liveness check - is the application running?
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        // Readiness check - is the application ready to serve requests?
        Map<String, Object> response = new LinkedHashMap<>();

        boolean isDatabaseReady = "UP".equals(checkDatabase().get("status"));

        response.put("status", isDatabaseReady ? "UP" : "DOWN");
        response.put("timestamp", LocalDateTime.now());
        response.put("checks", Map.of(
                "database", isDatabaseReady ? "READY" : "NOT_READY"
        ));

        return ResponseEntity
                .status(isDatabaseReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> dbStatus = new LinkedHashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5 second timeout

            dbStatus.put("status", isValid ? "UP" : "DOWN");
            dbStatus.put("message", isValid ? "Database connection successful" : "Database connection invalid");

            // Only include non-sensitive details
            if (isValid) {
                dbStatus.put("details", Map.of(
                        "database", connection.getCatalog(),
                        "validationTimeout", "5 seconds"
                ));
            }

        } catch (SQLException e) {
            dbStatus.put("status", "DOWN");
            dbStatus.put("message", "Database connection failed");
            dbStatus.put("error", e.getMessage());
        }

        return dbStatus;
    }

    private Map<String, Object> checkMemory() {
        Map<String, Object> memoryStatus = new LinkedHashMap<>();

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double usagePercent = (double) usedMemory / maxMemory * 100;

        String status;
        if (usagePercent < 80) {
            status = "UP";
        } else if (usagePercent < 90) {
            status = "WARNING";
        } else {
            status = "DOWN";
        }

        memoryStatus.put("status", status);
        memoryStatus.put("message", String.format("Memory usage: %.1f%%", usagePercent));
        memoryStatus.put("details", Map.of(
                "used", formatBytes(usedMemory),
                "free", formatBytes(freeMemory),
                "total", formatBytes(totalMemory),
                "max", formatBytes(maxMemory),
                "usagePercent", String.format("%.1f", usagePercent)
        ));

        return memoryStatus;
    }

    private Map<String, Object> checkDiskSpace() {
        Map<String, Object> diskStatus = new LinkedHashMap<>();

        java.io.File root = new java.io.File(".");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getFreeSpace();
        long usableSpace = root.getUsableSpace();

        double usagePercent = (double) (totalSpace - freeSpace) / totalSpace * 100;

        String status;
        if (usagePercent < 80) {
            status = "UP";
        } else if (usagePercent < 90) {
            status = "WARNING";
        } else {
            status = "DOWN";
        }

        diskStatus.put("status", status);
        diskStatus.put("message", String.format("Disk usage: %.1f%%", usagePercent));
        diskStatus.put("details", Map.of(
                "free", formatBytes(freeSpace),
                "usable", formatBytes(usableSpace),
                "total", formatBytes(totalSpace),
                "usagePercent", String.format("%.1f", usagePercent)
        ));

        return diskStatus;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}