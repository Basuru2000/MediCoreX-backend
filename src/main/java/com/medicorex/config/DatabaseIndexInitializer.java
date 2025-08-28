package com.medicorex.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Slf4j
@Configuration
public class DatabaseIndexInitializer {

    @Bean
    public CommandLineRunner createIndexes(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            // Create indexes only if they don't exist
            createIndexIfNotExists(jdbc, "notifications", "idx_notifications_expires_at", "expires_at");
            createIndexIfNotExists(jdbc, "notifications", "idx_notifications_user_status", "user_id, status, created_at DESC");
            createIndexIfNotExists(jdbc, "product_batches", "idx_batch_product_status", "product_id, status");
            createIndexIfNotExists(jdbc, "expiry_alerts", "idx_expiry_alert_batch_status", "batch_id, status");
            createIndexIfNotExists(jdbc, "stock_transactions", "idx_stock_trans_product_date", "product_id, transaction_date DESC");
            createIndexIfNotExists(jdbc, "quarantine_records", "idx_quarantine_date_status", "quarantine_date, status");
            createIndexIfNotExists(jdbc, "products", "idx_products_code", "code");
            createIndexIfNotExists(jdbc, "products", "idx_products_barcode", "barcode");
            createIndexIfNotExists(jdbc, "users", "idx_users_email", "email");

            log.info("Database indexes verified/created successfully");
        };
    }

    private void createIndexIfNotExists(JdbcTemplate jdbc, String table, String indexName, String columns) {
        try {
            // Check if index exists
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                            "AND table_name = ? AND index_name = ?",
                    Integer.class, table, indexName
            );

            if (count == 0) {
                jdbc.execute(String.format("ALTER TABLE %s ADD INDEX %s (%s)", table, indexName, columns));
                log.info("Created index: {} on {}", indexName, table);
            }
        } catch (Exception e) {
            log.debug("Index {} might already exist: {}", indexName, e.getMessage());
        }
    }
}