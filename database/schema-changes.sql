-- =====================================================
-- MediCoreX Database Schema Changes Log
-- =====================================================
-- Purpose: Track all database changes during development
-- Note: Run these queries in order when setting up a new database
-- =====================================================

-- =====================================================
-- Date: 2024-01-01
-- Feature: Initial Database Setup
-- Developer: Initial Setup
-- Status: APPLIED ✓
-- =====================================================
CREATE DATABASE IF NOT EXISTS medicorex_db;
USE medicorex_db;

-- Categories table
CREATE TABLE IF NOT EXISTS categories (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          name VARCHAR(100) UNIQUE NOT NULL,
                                          description TEXT
);

-- Users table
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(50) UNIQUE NOT NULL,
                                     password VARCHAR(255) NOT NULL,
                                     email VARCHAR(100) UNIQUE NOT NULL,
                                     full_name VARCHAR(100) NOT NULL,
                                     role VARCHAR(50) NOT NULL,
                                     active BOOLEAN DEFAULT TRUE,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Products table
CREATE TABLE IF NOT EXISTS products (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        name VARCHAR(200) NOT NULL,
                                        code VARCHAR(50) UNIQUE,
                                        description TEXT,
                                        category_id BIGINT NOT NULL,
                                        quantity INT DEFAULT 0,
                                        min_stock_level INT NOT NULL,
                                        unit VARCHAR(50) NOT NULL,
                                        unit_price DECIMAL(10,2) NOT NULL,
                                        expiry_date DATE,
                                        batch_number VARCHAR(50),
                                        manufacturer VARCHAR(100),
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                        FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Insert initial categories
INSERT INTO categories (name, description) VALUES
                                               ('Medications', 'Pharmaceutical drugs and medicines'),
                                               ('Medical Supplies', 'Consumable medical supplies'),
                                               ('Equipment', 'Medical equipment and devices')
ON DUPLICATE KEY UPDATE name=name;

-- =====================================================
-- Date: 2024-01-15
-- Feature: Stock Management (Week 3)
-- Developer: Week 3 Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Stock transactions table for tracking all inventory movements
CREATE TABLE IF NOT EXISTS stock_transactions (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  product_id BIGINT NOT NULL,
                                                  quantity INT NOT NULL,
                                                  balance_after INT NOT NULL,
                                                  type VARCHAR(20) NOT NULL,
                                                  reason VARCHAR(255) NOT NULL,
                                                  reference VARCHAR(100),
                                                  performed_by BIGINT NOT NULL,
                                                  transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                  FOREIGN KEY (product_id) REFERENCES products(id),
                                                  FOREIGN KEY (performed_by) REFERENCES users(id)
);

-- =====================================================
-- Date: 2024-01-20
-- Feature: Product Image Support
-- Developer: Week 3-4 Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Add image URL column to products
ALTER TABLE products ADD COLUMN image_url VARCHAR(500) AFTER manufacturer;

-- Create file uploads tracking table
CREATE TABLE IF NOT EXISTS file_uploads (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            file_name VARCHAR(255) NOT NULL,
                                            file_type VARCHAR(100),
                                            file_size BIGINT,
                                            file_path VARCHAR(500) NOT NULL,
                                            uploaded_by BIGINT,
                                            uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

-- =====================================================
-- Date: 2024-01-22
-- Feature: Category Hierarchy Support
-- Developer: Week 3-4 Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Add parent_id for nested categories
ALTER TABLE categories ADD COLUMN parent_id BIGINT AFTER description;

-- Add foreign key constraint for parent-child relationship
ALTER TABLE categories
    ADD CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT;

-- Add index for better performance on hierarchy queries
CREATE INDEX idx_category_parent ON categories(parent_id);

-- =====================================================
-- Date: 2024-01-25
-- Feature: User Profile Images and Gender
-- Developer: Enhancement Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Add gender column with default value
ALTER TABLE users ADD COLUMN gender VARCHAR(20) DEFAULT 'NOT_SPECIFIED' AFTER role;

-- Add profile image URL column
ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) AFTER gender;

-- Update existing users to have gender value
-- Note: Requires disabling safe mode
SET SQL_SAFE_UPDATES = 0;
UPDATE users SET gender = 'NOT_SPECIFIED' WHERE gender IS NULL;
SET SQL_SAFE_UPDATES = 1;

-- =====================================================
-- Date: 2024-01-30
-- Feature: Barcode/QR Code Support
-- Developer: Week 3-4 Enhancement
-- Status: APPLIED ✓
-- =====================================================
-- Add barcode and QR code columns to products
ALTER TABLE products ADD COLUMN barcode VARCHAR(100) UNIQUE AFTER code;
ALTER TABLE products ADD COLUMN qr_code TEXT AFTER barcode;

-- Create index for faster barcode lookups
CREATE INDEX idx_product_barcode ON products(barcode);

-- =====================================================
-- Date: 2024-01-XX (Update with actual date)
-- Feature: Multi-tier Alert Configuration (Week 5)
-- Developer: Week 5 Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Expiry Alert Configuration table
CREATE TABLE IF NOT EXISTS expiry_alert_configs (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    tier_name VARCHAR(100) NOT NULL,
                                                    days_before_expiry INT NOT NULL,
                                                    severity ENUM('INFO', 'WARNING', 'CRITICAL') NOT NULL,
                                                    description TEXT,
                                                    active BOOLEAN DEFAULT TRUE NOT NULL,
                                                    notify_roles VARCHAR(255), -- Comma-separated roles
                                                    color_code VARCHAR(7), -- Hex color for UI display
                                                    sort_order INT DEFAULT 0,
                                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                    UNIQUE KEY unique_days_before (days_before_expiry),
                                                    INDEX idx_active_days (active, days_before_expiry)
);

-- Expiry Alerts table (for tracking generated alerts)
CREATE TABLE IF NOT EXISTS expiry_alerts (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             product_id BIGINT NOT NULL,
                                             config_id BIGINT NOT NULL,
                                             batch_number VARCHAR(50),
                                             alert_date DATE NOT NULL,
                                             expiry_date DATE NOT NULL,
                                             quantity_affected INT NOT NULL,
                                             status ENUM('PENDING', 'SENT', 'ACKNOWLEDGED', 'RESOLVED') DEFAULT 'PENDING',
                                             acknowledged_by BIGINT,
                                             acknowledged_at TIMESTAMP NULL,
                                             notes TEXT,
                                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                             updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                             FOREIGN KEY (product_id) REFERENCES products(id),
                                             FOREIGN KEY (config_id) REFERENCES expiry_alert_configs(id),
                                             FOREIGN KEY (acknowledged_by) REFERENCES users(id),
                                             INDEX idx_status_alert_date (status, alert_date),
                                             INDEX idx_product_status (product_id, status)
);

-- Insert default alert configurations
INSERT INTO expiry_alert_configs (tier_name, days_before_expiry, severity, description, notify_roles, color_code, sort_order) VALUES
                                                                                                                                  ('Critical Alert', 7, 'CRITICAL', 'Products expiring within 1 week', 'HOSPITAL_MANAGER,PHARMACY_STAFF', '#d32f2f', 1),
                                                                                                                                  ('High Priority', 30, 'WARNING', 'Products expiring within 30 days', 'HOSPITAL_MANAGER,PHARMACY_STAFF', '#f57c00', 2),
                                                                                                                                  ('Medium Priority', 60, 'WARNING', 'Products expiring within 60 days', 'PHARMACY_STAFF', '#fbc02d', 3),
                                                                                                                                  ('Low Priority', 90, 'INFO', 'Products expiring within 90 days', 'PHARMACY_STAFF', '#388e3c', 4),
                                                                                                                                  ('Early Warning', 180, 'INFO', 'Products expiring within 6 months', 'PROCUREMENT_OFFICER', '#1976d2', 5)
ON DUPLICATE KEY UPDATE tier_name=tier_name;

-- Add index for better query performance on products table
-- Note: This uses standard MySQL syntax, not conditional index
CREATE INDEX idx_product_expiry ON products(expiry_date);

-- =====================================================
-- Date: 2024-02-XX
-- Feature: Automated Daily Expiry Checks (Week 5)
-- Developer: Week 5 Implementation
-- Status: PENDING
-- =====================================================
-- Expiry Check Log table to track scheduled job execution
CREATE TABLE IF NOT EXISTS expiry_check_logs (
                                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 check_date DATE NOT NULL,
                                                 start_time TIMESTAMP NOT NULL,
                                                 end_time TIMESTAMP NULL,
                                                 status ENUM('RUNNING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'RUNNING',
                                                 products_checked INT DEFAULT 0,
                                                 alerts_generated INT DEFAULT 0,
                                                 error_message TEXT,
                                                 execution_time_ms BIGINT,
                                                 created_by VARCHAR(50) DEFAULT 'SYSTEM',
                                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                 UNIQUE KEY unique_check_date (check_date),
                                                 INDEX idx_status_date (status, check_date)
);

-- Add composite index for efficient expiry date queries
CREATE INDEX idx_product_expiry_quantity ON products(expiry_date, quantity);

-- Add batch processing status to alerts
ALTER TABLE expiry_alerts
    ADD COLUMN check_log_id BIGINT AFTER config_id,
    ADD CONSTRAINT fk_alert_check_log
        FOREIGN KEY (check_log_id) REFERENCES expiry_check_logs(id);


-- =====================================================
-- Date: 2024-02-XX (Update with today's date)
-- Feature: Allow Multiple Manual Expiry Checks (Week 5 Enhancement)
-- Developer: Week 5 Implementation
-- Status: PENDING -> Change to APPLIED ✓ after running
-- =====================================================
-- Purpose: Remove unique constraint on check_date to allow multiple manual checks
-- during development and testing. The application logic controls whether
-- multiple checks are allowed based on configuration.

-- Check if the constraint exists before dropping
-- Note: The constraint name might vary, check with:
-- SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
-- WHERE TABLE_SCHEMA = 'medicorex_db' AND TABLE_NAME = 'expiry_check_logs' AND CONSTRAINT_TYPE = 'UNIQUE';

-- Drop the unique constraint on check_date
ALTER TABLE expiry_check_logs DROP INDEX unique_check_date;

-- Add a composite index for better query performance
-- This maintains fast lookups while allowing multiple checks per date
CREATE INDEX idx_check_date_status ON expiry_check_logs(check_date, status);

-- Add index for queries by date and start time
CREATE INDEX idx_check_date_start_time ON expiry_check_logs(check_date, start_time DESC);

-- Note: After applying this change, the application can be configured to:
-- 1. Allow multiple manual checks: expiry.check.allow-multiple-manual=true
-- 2. Restrict to one check per day: expiry.check.allow-multiple-manual=false
--    (restriction handled at application level, not database level)


-- =====================================================
-- Date: 2024-02-XX (Update with actual date)
-- Feature: Batch-wise Expiry Tracking (Week 5.3)
-- Developer: Week 5 Implementation
-- Status: PENDING -> Change to APPLIED ✓ after running
-- =====================================================

-- Product Batch table for tracking individual batches
CREATE TABLE IF NOT EXISTS product_batches (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               product_id BIGINT NOT NULL,
                                               batch_number VARCHAR(50) NOT NULL,
                                               quantity INT NOT NULL DEFAULT 0,
                                               initial_quantity INT NOT NULL,
                                               expiry_date DATE NOT NULL,
                                               manufacture_date DATE,
                                               supplier_reference VARCHAR(100),
                                               cost_per_unit DECIMAL(10,2),
                                               status ENUM('ACTIVE', 'DEPLETED', 'EXPIRED', 'QUARANTINED') NOT NULL DEFAULT 'ACTIVE',
                                               notes TEXT,
                                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                               FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                                               UNIQUE KEY unique_product_batch (product_id, batch_number),
                                               INDEX idx_expiry_date (expiry_date),
                                               INDEX idx_status (status),
                                               INDEX idx_product_status (product_id, status)
);

-- Batch Stock Transactions table
CREATE TABLE IF NOT EXISTS batch_stock_transactions (
                                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                        batch_id BIGINT NOT NULL,
                                                        quantity INT NOT NULL,
                                                        balance_after INT NOT NULL,
                                                        transaction_type ENUM('IN', 'OUT', 'ADJUSTMENT', 'EXPIRED', 'QUARANTINE') NOT NULL,
                                                        reference_type VARCHAR(50),
                                                        reference_id BIGINT,
                                                        reason VARCHAR(255),
                                                        performed_by BIGINT NOT NULL,
                                                        transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                        FOREIGN KEY (batch_id) REFERENCES product_batches(id),
                                                        FOREIGN KEY (performed_by) REFERENCES users(id),
                                                        INDEX idx_batch_date (batch_id, transaction_date)
);

-- Update expiry_alerts to track batch
ALTER TABLE expiry_alerts
    ADD COLUMN batch_id BIGINT AFTER product_id,
    ADD CONSTRAINT fk_alert_batch
        FOREIGN KEY (batch_id) REFERENCES product_batches(id);

-- Create index for batch-based alerts
CREATE INDEX idx_alert_batch ON expiry_alerts(batch_id, status);

-- Migrate existing product data to batches (one batch per product with existing data)
INSERT INTO product_batches (
    product_id,
    batch_number,
    quantity,
    initial_quantity,
    expiry_date,
    status
)
SELECT
    id,
    COALESCE(batch_number, CONCAT('BATCH-', id, '-', DATE_FORMAT(NOW(), '%Y%m%d'))),
    quantity,
    quantity,
    expiry_date,
    CASE
        WHEN quantity = 0 THEN 'DEPLETED'
        WHEN expiry_date < CURDATE() THEN 'EXPIRED'
        ELSE 'ACTIVE'
        END
FROM products
WHERE expiry_date IS NOT NULL;

-- Add trigger to update product quantity based on batch totals
DELIMITER $$
CREATE TRIGGER update_product_quantity_after_batch_change
    AFTER UPDATE ON product_batches
    FOR EACH ROW
BEGIN
    UPDATE products
    SET quantity = (
        SELECT COALESCE(SUM(quantity), 0)
        FROM product_batches
        WHERE product_id = NEW.product_id
          AND status IN ('ACTIVE', 'QUARANTINED')
    )
    WHERE id = NEW.product_id;
END$$
DELIMITER ;


-- =====================================================
-- END OF BATCH TRACKING SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Date: 2024-02-XX (Update with actual date)
-- Feature: 1.4 Expired Items Quarantine Workflow (Week 5)
-- Developer: Week 5 Implementation
-- Status: PENDING
-- =====================================================

-- Quarantine Records table to track quarantined items
CREATE TABLE IF NOT EXISTS quarantine_records (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  batch_id BIGINT NOT NULL,
                                                  product_id BIGINT NOT NULL,
                                                  quantity_quarantined INT NOT NULL,
                                                  reason VARCHAR(50) NOT NULL,
                                                  quarantine_date DATE NOT NULL,
                                                  quarantined_by VARCHAR(50) NOT NULL,
                                                  status ENUM('PENDING_REVIEW', 'UNDER_REVIEW', 'APPROVED_FOR_DISPOSAL',
                                                      'APPROVED_FOR_RETURN', 'DISPOSED', 'RETURNED') NOT NULL DEFAULT 'PENDING_REVIEW',
                                                  review_date DATETIME,
                                                  reviewed_by VARCHAR(50),
                                                  disposal_date DATETIME,
                                                  disposal_method VARCHAR(100),
                                                  disposal_certificate VARCHAR(255),
                                                  return_date DATETIME,
                                                  return_reference VARCHAR(100),
                                                  estimated_loss DECIMAL(10,2),
                                                  notes TEXT,
                                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                  FOREIGN KEY (batch_id) REFERENCES product_batches(id),
                                                  FOREIGN KEY (product_id) REFERENCES products(id),
                                                  INDEX idx_quarantine_status (status),
                                                  INDEX idx_quarantine_date (quarantine_date),
                                                  INDEX idx_quarantine_batch (batch_id)
);

-- Quarantine Action Log for audit trail
CREATE TABLE IF NOT EXISTS quarantine_action_logs (
                                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                      quarantine_record_id BIGINT NOT NULL,
                                                      action VARCHAR(50) NOT NULL,
                                                      performed_by VARCHAR(50) NOT NULL,
                                                      performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                      previous_status VARCHAR(50),
                                                      new_status VARCHAR(50),
                                                      comments TEXT,

                                                      FOREIGN KEY (quarantine_record_id) REFERENCES quarantine_records(id),
                                                      INDEX idx_action_record (quarantine_record_id)
);

-- Add quarantine reference to expiry alerts
ALTER TABLE expiry_alerts
    ADD COLUMN quarantine_record_id BIGINT AFTER batch_id,
    ADD CONSTRAINT fk_alert_quarantine
        FOREIGN KEY (quarantine_record_id) REFERENCES quarantine_records(id);

-- Create view for quarantine summary
CREATE OR REPLACE VIEW quarantine_summary_view AS
SELECT
    COUNT(*) as total_items,
    SUM(quantity_quarantined) as total_quantity,
    SUM(estimated_loss) as total_estimated_loss,
    SUM(CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE 0 END) as pending_review,
    SUM(CASE WHEN status = 'UNDER_REVIEW' THEN 1 ELSE 0 END) as under_review,
    SUM(CASE WHEN status IN ('APPROVED_FOR_DISPOSAL', 'DISPOSED') THEN 1 ELSE 0 END) as disposal_count,
    SUM(CASE WHEN status IN ('APPROVED_FOR_RETURN', 'RETURNED') THEN 1 ELSE 0 END) as return_count,
    DATE(quarantine_date) as date_grouped
FROM quarantine_records
GROUP BY DATE(quarantine_date);

-- =====================================================
-- END OF QUARANTINE WORKFLOW SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Date: 2024-02-XX (Update with actual date)
-- Feature: 2.1 In-app Notification Center (Week 5)
-- Developer: Week 5 Implementation Phase 2
-- Status: PENDING
-- =====================================================

-- Notifications table to store all system notifications
CREATE TABLE IF NOT EXISTS notifications (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             user_id BIGINT NOT NULL,
                                             type VARCHAR(50) NOT NULL,
                                             category VARCHAR(50) NOT NULL,
                                             title VARCHAR(255) NOT NULL,
                                             message TEXT NOT NULL,
                                             priority ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') DEFAULT 'MEDIUM',
                                             status ENUM('UNREAD', 'READ', 'ARCHIVED') DEFAULT 'UNREAD',
                                             action_url VARCHAR(500),
                                             action_data JSON,
                                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                             read_at TIMESTAMP NULL,
                                             expires_at TIMESTAMP NULL,

                                             FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                             INDEX idx_user_status (user_id, status),
                                             INDEX idx_created_at (created_at),
                                             INDEX idx_type_category (type, category)
);

-- Notification preferences table for user settings (preparation for 2.2)
CREATE TABLE IF NOT EXISTS notification_preferences (
                                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                        user_id BIGINT NOT NULL UNIQUE,
                                                        email_enabled BOOLEAN DEFAULT TRUE,
                                                        sms_enabled BOOLEAN DEFAULT FALSE,
                                                        in_app_enabled BOOLEAN DEFAULT TRUE,
                                                        quarantine_notifications BOOLEAN DEFAULT TRUE,
                                                        stock_notifications BOOLEAN DEFAULT TRUE,
                                                        expiry_notifications BOOLEAN DEFAULT TRUE,
                                                        batch_notifications BOOLEAN DEFAULT TRUE,
                                                        system_notifications BOOLEAN DEFAULT TRUE,
                                                        approval_notifications BOOLEAN DEFAULT TRUE,
                                                        report_notifications BOOLEAN DEFAULT TRUE,
                                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Notification templates for consistent messaging
CREATE TABLE IF NOT EXISTS notification_templates (
                                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                      code VARCHAR(100) NOT NULL UNIQUE,
                                                      category VARCHAR(50) NOT NULL,
                                                      title_template VARCHAR(500) NOT NULL,
                                                      message_template TEXT NOT NULL,
                                                      priority ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') DEFAULT 'MEDIUM',
                                                      active BOOLEAN DEFAULT TRUE,
                                                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert default notification templates
INSERT INTO notification_templates (code, category, title_template, message_template, priority) VALUES
-- Quarantine Templates
('QUARANTINE_NEW', 'QUARANTINE', 'New Quarantine Record', 'Product {{productName}} (Batch: {{batchNumber}}) has been quarantined. Reason: {{reason}}', 'HIGH'),
('QUARANTINE_APPROVED_DISPOSAL', 'QUARANTINE', 'Quarantine Item Approved for Disposal', 'Product {{productName}} has been approved for disposal. Please proceed with disposal process.', 'HIGH'),
('QUARANTINE_APPROVED_RETURN', 'QUARANTINE', 'Quarantine Item Approved for Return', 'Product {{productName}} has been approved for return to supplier.', 'MEDIUM'),
('QUARANTINE_DISPOSED', 'QUARANTINE', 'Item Disposed', 'Product {{productName}} (Batch: {{batchNumber}}) has been disposed. Method: {{method}}', 'MEDIUM'),
('QUARANTINE_RETURNED', 'QUARANTINE', 'Item Returned to Supplier', 'Product {{productName}} (Batch: {{batchNumber}}) has been successfully returned.', 'MEDIUM'),
('QUARANTINE_ESCALATION', 'QUARANTINE', 'Quarantine Escalation Alert', '{{count}} quarantine items require immediate attention.', 'CRITICAL'),

-- Stock Templates
('STOCK_LOW', 'STOCK', 'Low Stock Alert', 'Product {{productName}} is running low. Current quantity: {{quantity}}, Minimum: {{minStock}}', 'HIGH'),
('STOCK_OUT', 'STOCK', 'Out of Stock', 'Product {{productName}} is now out of stock!', 'CRITICAL'),
('STOCK_ADJUSTED', 'STOCK', 'Stock Adjustment', 'Stock for {{productName}} has been adjusted. New quantity: {{quantity}}', 'LOW'),
('STOCK_RECEIVED', 'STOCK', 'Stock Received', 'New stock received for {{productName}}. Quantity: {{quantity}}', 'LOW'),

-- Expiry Templates
('EXPIRY_7_DAYS', 'EXPIRY', 'Product Expiring Soon', 'Product {{productName}} (Batch: {{batchNumber}}) will expire in 7 days!', 'HIGH'),
('EXPIRY_30_DAYS', 'EXPIRY', 'Product Expiry Alert', 'Product {{productName}} (Batch: {{batchNumber}}) will expire in 30 days.', 'MEDIUM'),
('EXPIRY_60_DAYS', 'EXPIRY', 'Product Expiry Notice', 'Product {{productName}} (Batch: {{batchNumber}}) will expire in 60 days.', 'LOW'),
('EXPIRY_90_DAYS', 'EXPIRY', 'Product Expiry Reminder', 'Product {{productName}} (Batch: {{batchNumber}}) will expire in 90 days.', 'LOW'),
('EXPIRED', 'EXPIRY', 'Product Expired', 'Product {{productName}} (Batch: {{batchNumber}}) has expired and moved to quarantine.', 'CRITICAL'),

-- Batch Templates
('BATCH_CREATED', 'BATCH', 'New Batch Created', 'New batch {{batchNumber}} created for {{productName}}', 'LOW'),
('BATCH_DEPLETED', 'BATCH', 'Batch Depleted', 'Batch {{batchNumber}} of {{productName}} is now depleted.', 'MEDIUM'),
('BATCH_EXPIRING', 'BATCH', 'Batch Expiring Soon', 'Batch {{batchNumber}} of {{productName}} will expire on {{expiryDate}}', 'HIGH'),

-- User Templates
('USER_CREATED', 'USER', 'New User Account', 'A new user account has been created for {{username}}', 'LOW'),
('USER_ROLE_CHANGED', 'USER', 'Role Updated', 'Your role has been updated to {{role}}', 'MEDIUM'),
('USER_STATUS_CHANGED', 'USER', 'Account Status Changed', 'Your account status has been {{status}}', 'HIGH'),
('USER_PASSWORD_RESET', 'USER', 'Password Reset', 'Your password has been reset successfully.', 'HIGH'),

-- System Templates
('SYSTEM_MAINTENANCE', 'SYSTEM', 'System Maintenance', 'System maintenance scheduled for {{date}} from {{startTime}} to {{endTime}}', 'HIGH'),
('SYSTEM_UPDATE', 'SYSTEM', 'System Update', 'System has been updated to version {{version}}', 'MEDIUM'),
('SYSTEM_ANNOUNCEMENT', 'SYSTEM', 'System Announcement', '{{message}}', 'MEDIUM'),

-- Approval Templates
('APPROVAL_PENDING', 'APPROVAL', 'Approval Required', '{{itemType}} requires your approval: {{itemName}}', 'HIGH'),
('APPROVAL_GRANTED', 'APPROVAL', 'Approval Granted', 'Your request for {{itemName}} has been approved.', 'MEDIUM'),
('APPROVAL_REJECTED', 'APPROVAL', 'Approval Rejected', 'Your request for {{itemName}} has been rejected. Reason: {{reason}}', 'MEDIUM'),

-- Report Templates
('REPORT_READY', 'REPORT', 'Report Generated', 'Your {{reportType}} report is ready for download.', 'LOW'),
('REPORT_SCHEDULED', 'REPORT', 'Scheduled Report', 'Your scheduled {{reportType}} report has been generated.', 'LOW');

-- Create view for notification summary
CREATE OR REPLACE VIEW notification_summary_view AS
SELECT
    u.id as user_id,
    u.username,
    COUNT(CASE WHEN n.status = 'UNREAD' THEN 1 END) as unread_count,
    COUNT(CASE WHEN n.status = 'READ' THEN 1 END) as read_count,
    COUNT(CASE WHEN n.priority = 'CRITICAL' AND n.status = 'UNREAD' THEN 1 END) as critical_unread,
    COUNT(CASE WHEN n.priority = 'HIGH' AND n.status = 'UNREAD' THEN 1 END) as high_priority_unread,
    MAX(n.created_at) as latest_notification
FROM users u
         LEFT JOIN notifications n ON u.id = n.user_id
GROUP BY u.id, u.username;

-- Add notification count to user sessions (for caching)
ALTER TABLE users
    ADD COLUMN unread_notifications INT DEFAULT 0,
    ADD COLUMN last_notification_check TIMESTAMP NULL;

-- Create trigger to update unread count
DELIMITER $$
CREATE TRIGGER update_unread_notification_count
    AFTER INSERT ON notifications
    FOR EACH ROW
BEGIN
    UPDATE users
    SET unread_notifications = (
        SELECT COUNT(*)
        FROM notifications
        WHERE user_id = NEW.user_id AND status = 'UNREAD'
    )
    WHERE id = NEW.user_id;
END$$

CREATE TRIGGER update_unread_count_on_read
    AFTER UPDATE ON notifications
    FOR EACH ROW
BEGIN
    IF OLD.status != NEW.status THEN
        UPDATE users
        SET unread_notifications = (
            SELECT COUNT(*)
            FROM notifications
            WHERE user_id = NEW.user_id AND status = 'UNREAD'
        )
        WHERE id = NEW.user_id;
    END IF;
END$$
DELIMITER ;


-- Add missing columns to products table
-- Run this in your MySQL database
ALTER TABLE products
    ADD COLUMN min_stock INT DEFAULT 10 AFTER quantity,
    ADD COLUMN max_stock INT DEFAULT 1000 AFTER min_stock,
    ADD COLUMN last_updated TIMESTAMP NULL AFTER updated_at,
    ADD COLUMN last_stock_check TIMESTAMP NULL AFTER last_updated;

UPDATE products
SET min_stock = CASE
                    WHEN quantity < 10 THEN 5
                    WHEN quantity < 50 THEN 10
                    WHEN quantity < 100 THEN 20
                    ELSE 50
    END
WHERE min_stock IS NULL;

-- Update stock_transactions table to add missing columns
-- Run this in your MySQL database
ALTER TABLE stock_transactions
    ADD COLUMN notes TEXT AFTER reference,
    ADD COLUMN before_quantity INT AFTER performed_by,
    ADD COLUMN after_quantity INT AFTER before_quantity,
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP AFTER after_quantity;


-- =====================================================
-- FIX NOTIFICATION TEMPLATE MISMATCHES
-- Run this SQL to ensure templates match service code
-- =====================================================

-- Update existing templates or insert if they don't exist
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active) VALUES
-- Batch Templates (ensure these exist)
('BATCH_CREATED', 'BATCH', 'New Batch Created', 'New batch {{batchNumber}} created for {{productName}}', 'LOW', true),
('BATCH_EXPIRING', 'BATCH', 'Batch Expiring Soon', 'Batch {{batchNumber}} of {{productName}} expires in {{days}} days', 'HIGH', true),
('BATCH_EXPIRED', 'BATCH', 'Batch Expired', 'Batch {{batchNumber}} of {{productName}} has expired', 'CRITICAL', true),
('BATCH_DEPLETED', 'BATCH', 'Batch Depleted', 'Batch {{batchNumber}} of {{productName}} has been fully consumed', 'MEDIUM', true),

-- Quarantine Templates (fix naming)
('QUARANTINE_CREATED', 'QUARANTINE', 'Item Quarantined', 'Product {{productName}} (Batch: {{batchNumber}}) has been quarantined. Quantity: {{quantity}}. Reason: {{reason}}', 'HIGH', true),
('QUARANTINE_PENDING', 'QUARANTINE', 'Quarantine Review Required', '{{count}} items are pending review in quarantine', 'HIGH', true),
('QUARANTINE_APPROVED_DISPOSAL', 'QUARANTINE', 'Approved for Disposal', 'Product {{productName}} has been approved for disposal. Please proceed with disposal process.', 'HIGH', true),
('QUARANTINE_APPROVED_RETURN', 'QUARANTINE', 'Approved for Return', 'Product {{productName}} has been approved for return to supplier.', 'MEDIUM', true),
('QUARANTINE_DISPOSED', 'QUARANTINE', 'Item Disposed', 'Product {{productName}} (Batch: {{batchNumber}}) has been disposed.', 'MEDIUM', true),
('QUARANTINE_RETURNED', 'QUARANTINE', 'Item Returned', 'Product {{productName}} (Batch: {{batchNumber}}) has been returned to supplier.', 'MEDIUM', true)
ON DUPLICATE KEY UPDATE
                     title_template = VALUES(title_template),
                     message_template = VALUES(message_template),
                     priority = VALUES(priority),
                     active = true;

-- =====================================================
-- Fix Notification Templates
-- Run this to ensure all required templates exist
-- =====================================================

-- First, check if BATCH_CREATED template exists
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'BATCH_CREATED', 'BATCH', 'New Batch Created',
       'New batch {{batchNumber}} created for {{productName}} with quantity {{quantity}}',
       'MEDIUM', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'BATCH_CREATED'
);

-- Update QUARANTINE_NEW to QUARANTINE_CREATED if it exists
UPDATE notification_templates
SET code = 'QUARANTINE_CREATED'
WHERE code = 'QUARANTINE_NEW';

-- Ensure QUARANTINE_CREATED exists
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'QUARANTINE_CREATED', 'QUARANTINE', 'Item Quarantined',
       'Product {{productName}} (Batch: {{batchNumber}}) has been quarantined. Reason: {{reason}}',
       'HIGH', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'QUARANTINE_CREATED'
);

-- Ensure USER_REGISTERED exists
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'USER_REGISTERED', 'USER', 'New User Registration',
       'New user {{username}} ({{role}}) has been registered',
       'LOW', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'USER_REGISTERED'
);

-- Add other missing templates
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'USER_ROLE_CHANGED', 'USER', 'Role Updated',
       'Your role has been updated to {{role}}',
       'MEDIUM', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'USER_ROLE_CHANGED'
);

INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'USER_DEACTIVATED', 'USER', 'User Deactivated',
       'User {{username}} has been deactivated',
       'MEDIUM', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'USER_DEACTIVATED'
);

INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'USER_ACTIVATED', 'USER', 'User Activated',
       'User {{username}} has been activated',
       'LOW', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'USER_ACTIVATED'
);

-- Verify all templates are loaded
SELECT code, category, priority, active FROM notification_templates ORDER BY category, code;

-- =====================================================
-- Add Missing Notification Templates
-- Run this if you get "template not found" errors
-- =====================================================

-- Daily Summary Template
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'DAILY_SUMMARY', 'SYSTEM', 'Daily Summary',
       'Daily system summary for {{date}}. {{summaryDetails}}',
       'LOW', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'DAILY_SUMMARY'
);

-- Escalation Notice Template
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'ESCALATION_NOTICE', 'SYSTEM', 'Critical Notification Escalation',
       'Critical notification for {{originalUser}} has been unread for {{hoursOverdue}} hours: {{title}}',
       'CRITICAL', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'ESCALATION_NOTICE'
);

-- Quarantine Notification Template (generic)
INSERT INTO notification_templates (code, category, title_template, message_template, priority, active, created_at)
SELECT 'QUARANTINE_NOTIFICATION', 'QUARANTINE', '{{title}}',
       '{{message}}',
       'HIGH', TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notification_templates WHERE code = 'QUARANTINE_NOTIFICATION'
);


-- =====================================================
-- END OF NOTIFICATION SYSTEM SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Date: 2024-02-XX (Update with actual date)
-- Feature: 2.2 Customizable Alert Preferences (Week 5)
-- Developer: Week 5 Implementation Phase 2.2
-- Status: PENDING
-- =====================================================

-- Drop existing notification_preferences table if it exists (from previous partial implementation)
DROP TABLE IF EXISTS notification_preferences;

-- Enhanced Notification Preferences table with full feature set
CREATE TABLE notification_preferences (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          user_id BIGINT NOT NULL UNIQUE,

    -- Global notification settings
                                          in_app_enabled BOOLEAN DEFAULT TRUE,
                                          email_enabled BOOLEAN DEFAULT FALSE,
                                          sms_enabled BOOLEAN DEFAULT FALSE,

    -- Category-specific preferences (stored as JSON)
    -- Format: {"STOCK": true, "EXPIRY": true, "BATCH": true, ...}
                                          category_preferences JSON,

    -- Priority threshold (only notify if priority >= this level)
    -- Values: LOW, MEDIUM, HIGH, CRITICAL
                                          priority_threshold VARCHAR(20) DEFAULT 'LOW',

    -- Quiet hours configuration (stored as JSON)
    -- Format: {"enabled": true, "startTime": "22:00", "endTime": "07:00", "timezone": "UTC"}
                                          quiet_hours JSON,

    -- Notification frequency settings (stored as JSON)
    -- Format: {"STOCK": "IMMEDIATE", "EXPIRY": "DAILY_DIGEST", ...}
                                          frequency_settings JSON,

    -- Daily digest settings
                                          digest_enabled BOOLEAN DEFAULT FALSE,
                                          digest_time TIME DEFAULT '09:00:00',
                                          last_digest_sent TIMESTAMP NULL,

    -- Escalation preferences
                                          escalation_enabled BOOLEAN DEFAULT TRUE,
                                          escalation_contact VARCHAR(255),

    -- Sound/Visual preferences
                                          sound_enabled BOOLEAN DEFAULT TRUE,
                                          desktop_notifications BOOLEAN DEFAULT TRUE,

    -- Metadata
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100),
                                          updated_by VARCHAR(100),

                                          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                          INDEX idx_user_preferences (user_id),
                                          INDEX idx_digest_settings (digest_enabled, digest_time)
);

-- Initialize default preferences for existing users
INSERT INTO notification_preferences (
    user_id,
    in_app_enabled,
    email_enabled,
    priority_threshold,
    category_preferences,
    frequency_settings,
    quiet_hours,
    sound_enabled,
    desktop_notifications,
    created_by
)
SELECT
    u.id,
    TRUE, -- in_app_enabled
    FALSE, -- email_enabled
    CASE
        WHEN u.role = 'HOSPITAL_MANAGER' THEN 'LOW'
        WHEN u.role = 'PHARMACY_STAFF' THEN 'MEDIUM'
        ELSE 'HIGH'
        END, -- priority_threshold based on role
    JSON_OBJECT(
            'STOCK', TRUE,
            'EXPIRY', TRUE,
            'BATCH', TRUE,
            'QUARANTINE', TRUE,
            'USER', TRUE,
            'SYSTEM', TRUE,
            'APPROVAL', TRUE,
            'REPORT', TRUE,
            'PROCUREMENT', TRUE
    ), -- all categories enabled by default
    JSON_OBJECT(
            'STOCK', 'IMMEDIATE',
            'EXPIRY', 'IMMEDIATE',
            'BATCH', 'IMMEDIATE',
            'QUARANTINE', 'IMMEDIATE',
            'USER', 'IMMEDIATE',
            'SYSTEM', 'IMMEDIATE',
            'APPROVAL', 'IMMEDIATE',
            'REPORT', 'DAILY_DIGEST',
            'PROCUREMENT', 'IMMEDIATE'
    ), -- frequency settings
    JSON_OBJECT(
            'enabled', FALSE,
            'startTime', '22:00',
            'endTime', '07:00',
            'timezone', 'UTC'
    ), -- quiet hours disabled by default
    TRUE, -- sound_enabled
    TRUE, -- desktop_notifications
    'SYSTEM' -- created_by
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM notification_preferences np WHERE np.user_id = u.id
);

-- Add preference check tracking to notifications table
ALTER TABLE notifications
    ADD COLUMN preference_checked BOOLEAN DEFAULT FALSE AFTER status,
    ADD COLUMN bypassed_quiet_hours BOOLEAN DEFAULT FALSE AFTER preference_checked,
    ADD INDEX idx_preference_check (preference_checked);

-- Create audit log for preference changes
CREATE TABLE IF NOT EXISTS notification_preference_audit (
                                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                             user_id BIGINT NOT NULL,
                                                             changed_by BIGINT NOT NULL,
                                                             change_type VARCHAR(50) NOT NULL,
                                                             old_value JSON,
                                                             new_value JSON,
                                                             change_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                                             FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                                             FOREIGN KEY (changed_by) REFERENCES users(id),
                                                             INDEX idx_audit_user (user_id),
                                                             INDEX idx_audit_timestamp (change_timestamp)
);

-- =====================================================
-- Stored Procedure for checking if notification should be sent
-- =====================================================
DELIMITER $$

CREATE PROCEDURE ShouldSendNotification(
    IN p_user_id BIGINT,
    IN p_category VARCHAR(50),
    IN p_priority VARCHAR(20),
    IN p_check_time TIMESTAMP,
    OUT p_should_send BOOLEAN,
    OUT p_reason VARCHAR(255)
)
proc_main: BEGIN  -- Added label here
    DECLARE v_in_app_enabled BOOLEAN DEFAULT FALSE;
    DECLARE v_category_enabled BOOLEAN DEFAULT FALSE;
    DECLARE v_priority_threshold VARCHAR(20);
    DECLARE v_quiet_hours JSON;
    DECLARE v_quiet_enabled BOOLEAN DEFAULT FALSE;
    DECLARE v_quiet_start TIME;
    DECLARE v_quiet_end TIME;
    DECLARE v_current_time TIME;
    DECLARE v_priority_level INT;
    DECLARE v_threshold_level INT;

    -- Get user preferences
    SELECT
        in_app_enabled,
        priority_threshold,
        quiet_hours,
        JSON_EXTRACT(category_preferences, CONCAT('$.', p_category))
    INTO
        v_in_app_enabled,
        v_priority_threshold,
        v_quiet_hours,
        v_category_enabled
    FROM notification_preferences
    WHERE user_id = p_user_id;

    -- Check if notifications are enabled
    IF NOT v_in_app_enabled THEN
        SET p_should_send = FALSE;
        SET p_reason = 'In-app notifications disabled';
        LEAVE proc_main;  -- Now references the label
    END IF;

    -- Check if category is enabled
    IF v_category_enabled IS NULL OR v_category_enabled = FALSE THEN
        SET p_should_send = FALSE;
        SET p_reason = CONCAT('Category ', p_category, ' disabled');
        LEAVE proc_main;  -- Now references the label
    END IF;

    -- Check priority threshold
    SET v_priority_level = CASE p_priority
                               WHEN 'CRITICAL' THEN 4
                               WHEN 'HIGH' THEN 3
                               WHEN 'MEDIUM' THEN 2
                               WHEN 'LOW' THEN 1
                               ELSE 0
        END;

    SET v_threshold_level = CASE v_priority_threshold
                                WHEN 'CRITICAL' THEN 4
                                WHEN 'HIGH' THEN 3
                                WHEN 'MEDIUM' THEN 2
                                WHEN 'LOW' THEN 1
                                ELSE 0
        END;

    IF v_priority_level < v_threshold_level THEN
        SET p_should_send = FALSE;
        SET p_reason = CONCAT('Priority ', p_priority, ' below threshold ', v_priority_threshold);
        LEAVE proc_main;  -- Now references the label
    END IF;

    -- Check quiet hours (skip for CRITICAL priority)
    IF p_priority != 'CRITICAL' AND v_quiet_hours IS NOT NULL THEN
        SET v_quiet_enabled = JSON_EXTRACT(v_quiet_hours, '$.enabled');

        IF v_quiet_enabled = TRUE THEN
            SET v_quiet_start = TIME(JSON_UNQUOTE(JSON_EXTRACT(v_quiet_hours, '$.startTime')));
            SET v_quiet_end = TIME(JSON_UNQUOTE(JSON_EXTRACT(v_quiet_hours, '$.endTime')));
            SET v_current_time = TIME(p_check_time);

            -- Handle overnight quiet hours
            IF v_quiet_start > v_quiet_end THEN
                IF v_current_time >= v_quiet_start OR v_current_time <= v_quiet_end THEN
                    SET p_should_send = FALSE;
                    SET p_reason = 'Within quiet hours';
                    LEAVE proc_main;  -- Now references the label
                END IF;
            ELSE
                IF v_current_time >= v_quiet_start AND v_current_time <= v_quiet_end THEN
                    SET p_should_send = FALSE;
                    SET p_reason = 'Within quiet hours';
                    LEAVE proc_main;  -- Now references the label
                END IF;
            END IF;
        END IF;
    END IF;

    -- All checks passed
    SET p_should_send = TRUE;
    SET p_reason = 'All preference checks passed';

END proc_main$$  -- Close the labeled block

DELIMITER ;

-- =====================================================
-- END OF NOTIFICATION PREFERENCES SCHEMA CHANGES
-- =====================================================


-- =====================================================
-- Phase 3.1: Critical Alerts Summary Widget
-- Date: Current Implementation
-- Purpose: Optimize database for dashboard queries
-- =====================================================

-- Add indexes for performance optimization on product_batches
CREATE INDEX idx_batch_expiry_status
    ON product_batches(expiry_date, status);

CREATE INDEX idx_batch_product_expiry
    ON product_batches(product_id, expiry_date);

CREATE INDEX idx_batch_status_quantity
    ON product_batches(status, quantity);

-- Add indexes for expiry alerts
CREATE INDEX idx_expiry_alert_status_severity
    ON expiry_alerts(status, config_id);

CREATE INDEX idx_expiry_alert_date_status
    ON expiry_alerts(alert_date, status);

-- Add indexes for quarantine records
CREATE INDEX idx_quarantine_status_date
    ON quarantine_records(status, quarantine_date);

-- Create materialized view for dashboard summary (MySQL doesn't support materialized views, so we'll use a regular view)
CREATE OR REPLACE VIEW expiry_dashboard_summary AS
SELECT
    -- Expired items
    (SELECT COUNT(*)
     FROM product_batches
     WHERE status = 'ACTIVE'
       AND expiry_date < CURDATE()) AS expired_count,

    -- Expiring today
    (SELECT COUNT(*)
     FROM product_batches
     WHERE status = 'ACTIVE'
       AND expiry_date = CURDATE()) AS expiring_today_count,

    -- Expiring this week
    (SELECT COUNT(*)
     FROM product_batches
     WHERE status = 'ACTIVE'
       AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)) AS expiring_week_count,

    -- Expiring this month
    (SELECT COUNT(*)
     FROM product_batches
     WHERE status = 'ACTIVE'
       AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)) AS expiring_month_count,

    -- Pending alerts
    (SELECT COUNT(*)
     FROM expiry_alerts
     WHERE status = 'PENDING') AS pending_alerts_count,

    -- Quarantined items
    (SELECT COUNT(*)
     FROM quarantine_records
     WHERE status IN ('PENDING_REVIEW', 'UNDER_REVIEW')) AS quarantined_count,

    -- Last check time
    (SELECT MAX(start_time)
     FROM expiry_check_logs
     WHERE status = 'COMPLETED') AS last_check_time;

-- Create summary table for caching dashboard data (optional, for performance)
CREATE TABLE IF NOT EXISTS expiry_summary_cache (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    summary_date DATE NOT NULL UNIQUE,
                                                    expired_count BIGINT DEFAULT 0,
                                                    expiring_today_count BIGINT DEFAULT 0,
                                                    expiring_week_count BIGINT DEFAULT 0,
                                                    expiring_month_count BIGINT DEFAULT 0,
                                                    pending_alerts_count BIGINT DEFAULT 0,
                                                    quarantined_count BIGINT DEFAULT 0,
                                                    total_value_at_risk DECIMAL(15,2) DEFAULT 0,
                                                    expired_value DECIMAL(15,2) DEFAULT 0,
                                                    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                    INDEX idx_summary_date (summary_date)
);

-- Stored procedure to refresh summary cache
DELIMITER $$

DROP PROCEDURE IF EXISTS RefreshExpirySummaryCache$$

CREATE PROCEDURE RefreshExpirySummaryCache()
BEGIN
    DECLARE v_expired_count BIGINT;
    DECLARE v_expiring_today BIGINT;
    DECLARE v_expiring_week BIGINT;
    DECLARE v_expiring_month BIGINT;
    DECLARE v_pending_alerts BIGINT;
    DECLARE v_quarantined BIGINT;
    DECLARE v_value_at_risk DECIMAL(15,2);
    DECLARE v_expired_value DECIMAL(15,2);

    -- Calculate counts
    SELECT COUNT(*) INTO v_expired_count
    FROM product_batches pb
    WHERE pb.status = 'ACTIVE'
      AND pb.expiry_date < CURDATE();

    SELECT COUNT(*) INTO v_expiring_today
    FROM product_batches pb
    WHERE pb.status = 'ACTIVE'
      AND pb.expiry_date = CURDATE();

    SELECT COUNT(*) INTO v_expiring_week
    FROM product_batches pb
    WHERE pb.status = 'ACTIVE'
      AND pb.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY);

    SELECT COUNT(*) INTO v_expiring_month
    FROM product_batches pb
    WHERE pb.status = 'ACTIVE'
      AND pb.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY);

    SELECT COUNT(*) INTO v_pending_alerts
    FROM expiry_alerts ea
    WHERE ea.status = 'PENDING';

    SELECT COUNT(*) INTO v_quarantined
    FROM quarantine_records qr
    WHERE qr.status IN ('PENDING_REVIEW', 'UNDER_REVIEW');

    -- Calculate values
    SELECT COALESCE(SUM(pb.quantity * p.unit_price), 0) INTO v_value_at_risk
    FROM product_batches pb
             JOIN products p ON pb.product_id = p.id
    WHERE pb.status = 'ACTIVE'
      AND pb.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY);

    SELECT COALESCE(SUM(pb.quantity * p.unit_price), 0) INTO v_expired_value
    FROM product_batches pb
             JOIN products p ON pb.product_id = p.id
    WHERE pb.status = 'ACTIVE'
      AND pb.expiry_date < CURDATE();

    -- Insert or update cache
    INSERT INTO expiry_summary_cache (
        summary_date,
        expired_count,
        expiring_today_count,
        expiring_week_count,
        expiring_month_count,
        pending_alerts_count,
        quarantined_count,
        total_value_at_risk,
        expired_value
    ) VALUES (
                 CURDATE(),
                 v_expired_count,
                 v_expiring_today,
                 v_expiring_week,
                 v_expiring_month,
                 v_pending_alerts,
                 v_quarantined,
                 v_value_at_risk,
                 v_expired_value
             )
    ON DUPLICATE KEY UPDATE
                         expired_count = VALUES(expired_count),
                         expiring_today_count = VALUES(expiring_today_count),
                         expiring_week_count = VALUES(expiring_week_count),
                         expiring_month_count = VALUES(expiring_month_count),
                         pending_alerts_count = VALUES(pending_alerts_count),
                         quarantined_count = VALUES(quarantined_count),
                         total_value_at_risk = VALUES(total_value_at_risk),
                         expired_value = VALUES(expired_value),
                         last_update_time = CURRENT_TIMESTAMP;
END$$

DELIMITER ;

-- Schedule the cache refresh (if using MySQL events)
-- Note: Ensure event scheduler is enabled: SET GLOBAL event_scheduler = ON;
CREATE EVENT IF NOT EXISTS refresh_expiry_summary_event
    ON SCHEDULE EVERY 1 HOUR
    DO CALL RefreshExpirySummaryCache();

-- Initial data population
CALL RefreshExpirySummaryCache();

-- =====================================================
-- End of Phase 3.1 Schema Changes
-- =====================================================

-- =====================================================
-- Date: 2024-12-XX (Update with actual date)
-- Feature: 3.2 Expiry Trends Analysis (Week 5)
-- Developer: Week 5 Implementation
-- Status: APPLIED ✓
-- =====================================================

-- Table to store daily expiry trend snapshots
CREATE TABLE IF NOT EXISTS expiry_trend_snapshots (
                                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                      snapshot_date DATE NOT NULL,
                                                      total_products INT NOT NULL DEFAULT 0,
                                                      expired_count INT NOT NULL DEFAULT 0,
                                                      expiring_7_days INT NOT NULL DEFAULT 0,
                                                      expiring_30_days INT NOT NULL DEFAULT 0,
                                                      expiring_60_days INT NOT NULL DEFAULT 0,
                                                      expiring_90_days INT NOT NULL DEFAULT 0,
                                                      expired_value DECIMAL(15,2) DEFAULT 0,
                                                      expiring_7_days_value DECIMAL(15,2) DEFAULT 0,
                                                      expiring_30_days_value DECIMAL(15,2) DEFAULT 0,
                                                      avg_days_to_expiry DECIMAL(10,2),
                                                      critical_category_id BIGINT,
                                                      critical_category_name VARCHAR(100),
                                                      critical_category_count INT,
                                                      trend_direction ENUM('IMPROVING', 'STABLE', 'WORSENING') DEFAULT 'STABLE',
                                                      trend_percentage DECIMAL(5,2),
                                                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                                      INDEX idx_snapshot_date (snapshot_date),
                                                      INDEX idx_trend_direction (trend_direction),
                                                      UNIQUE KEY unique_snapshot_date (snapshot_date)
);

-- Table for predictive analysis data
CREATE TABLE IF NOT EXISTS expiry_predictions (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  prediction_date DATE NOT NULL,
                                                  target_date DATE NOT NULL,
                                                  predicted_expiry_count INT NOT NULL,
                                                  confidence_level DECIMAL(5,2),
                                                  algorithm_used VARCHAR(50),
                                                  actual_count INT,
                                                  accuracy_percentage DECIMAL(5,2),
                                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                  updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,

                                                  INDEX idx_prediction_date (prediction_date),
                                                  INDEX idx_target_date (target_date),
                                                  UNIQUE KEY unique_prediction (prediction_date, target_date)
);

-- Historical trend aggregation view
CREATE OR REPLACE VIEW expiry_trend_summary AS
SELECT
    DATE_FORMAT(snapshot_date, '%Y-%m') as month,
    AVG(expired_count) as avg_expired,
    AVG(expiring_30_days) as avg_expiring_30,
    SUM(expired_value) as total_expired_value,
    COUNT(*) as data_points
FROM expiry_trend_snapshots
GROUP BY DATE_FORMAT(snapshot_date, '%Y-%m');

-- Stored procedure to calculate trend data
DELIMITER $$

CREATE PROCEDURE CalculateExpiryTrends(IN days_back INT)
BEGIN
    DECLARE v_trend_direction VARCHAR(20);
    DECLARE v_trend_percentage DECIMAL(5,2);
    DECLARE v_current_expired INT;
    DECLARE v_previous_expired INT;

    -- Get current expired count
    SELECT COUNT(*) INTO v_current_expired
    FROM product_batches
    WHERE status = 'ACTIVE' AND expiry_date < CURDATE();

    -- Get previous period expired count
    SELECT COALESCE(AVG(expired_count), 0) INTO v_previous_expired
    FROM expiry_trend_snapshots
    WHERE snapshot_date BETWEEN DATE_SUB(CURDATE(), INTERVAL days_back DAY)
              AND DATE_SUB(CURDATE(), INTERVAL 1 DAY);

    -- Calculate trend
    IF v_previous_expired = 0 THEN
        SET v_trend_percentage = 0;
        SET v_trend_direction = 'STABLE';
    ELSE
        SET v_trend_percentage = ((v_current_expired - v_previous_expired) / v_previous_expired) * 100;

        IF v_trend_percentage > 10 THEN
            SET v_trend_direction = 'WORSENING';
        ELSEIF v_trend_percentage < -10 THEN
            SET v_trend_direction = 'IMPROVING';
        ELSE
            SET v_trend_direction = 'STABLE';
        END IF;
    END IF;

    -- Return results
    SELECT v_trend_direction as trend_direction,
           v_trend_percentage as trend_percentage,
           v_current_expired as current_count,
           v_previous_expired as previous_avg;
END$$

DELIMITER ;

-- =====================================================
-- Date: Current Implementation Date
-- Feature: 3.3 Expiry Calendar Widget (Week 5)
-- Developer: Week 5 Implementation Phase 3.3
-- Status: APPLIED ✓
-- =====================================================

-- Add calendar-specific indexes for performance
-- Note: MySQL doesn't support filtered indexes, so we create regular indexes
CREATE INDEX idx_batch_expiry_calendar
    ON product_batches(expiry_date, status, quantity);

CREATE INDEX idx_alert_calendar
    ON expiry_alerts(expiry_date, status, alert_date);

-- Create materialized view for calendar data (MySQL alternative using table)
DROP TABLE IF EXISTS expiry_calendar_cache;
CREATE TABLE expiry_calendar_cache (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       event_date DATE NOT NULL,
                                       event_type ENUM('BATCH_EXPIRY', 'PRODUCT_EXPIRY', 'ALERT', 'QUARANTINE') NOT NULL,
                                       severity ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL,
                                       item_count INT NOT NULL DEFAULT 0,
                                       total_quantity INT NOT NULL DEFAULT 0,
                                       total_value DECIMAL(10,2) DEFAULT 0,
                                       details JSON,
                                       last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       INDEX idx_calendar_date (event_date),
    -- Create separate columns for indexing instead of functional index
                                       event_year INT GENERATED ALWAYS AS (YEAR(event_date)) STORED,
                                       event_month INT GENERATED ALWAYS AS (MONTH(event_date)) STORED,
                                       INDEX idx_calendar_month (event_year, event_month),
                                       UNIQUE KEY unique_date_type (event_date, event_type)
);

-- Stored procedure to refresh calendar cache (FIXED for ONLY_FULL_GROUP_BY)
-- Fixed stored procedure that doesn't insert into generated columns
DELIMITER $$

DROP PROCEDURE IF EXISTS RefreshExpiryCalendarCache$$
CREATE PROCEDURE RefreshExpiryCalendarCache(
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    DECLARE v_current_date DATE;

    -- Temporarily change SQL mode to avoid GROUP BY issues
    SET @original_sql_mode = @@sql_mode;
    SET sql_mode = (SELECT REPLACE(@@sql_mode, 'ONLY_FULL_GROUP_BY', ''));

    -- Clear existing cache for date range
    DELETE FROM expiry_calendar_cache
    WHERE event_date BETWEEN p_start_date AND p_end_date;

    -- Insert batch expiry events (REMOVED event_year and event_month from INSERT)
    INSERT INTO expiry_calendar_cache (
        event_date,
        event_type,
        severity,
        item_count,
        total_quantity,
        total_value,
        details
    )
    SELECT
        pb.expiry_date,
        'BATCH_EXPIRY',
        CASE
            WHEN MIN(DATEDIFF(pb.expiry_date, CURDATE())) <= 7 THEN 'CRITICAL'
            WHEN MIN(DATEDIFF(pb.expiry_date, CURDATE())) <= 30 THEN 'HIGH'
            WHEN MIN(DATEDIFF(pb.expiry_date, CURDATE())) <= 60 THEN 'MEDIUM'
            ELSE 'LOW'
            END,
        COUNT(*),
        SUM(pb.quantity),
        SUM(pb.quantity * COALESCE(pb.cost_per_unit, p.unit_price)),
        JSON_OBJECT(
                'batches', JSON_ARRAYAGG(
                JSON_OBJECT(
                        'batchId', pb.id,
                        'batchNumber', pb.batch_number,
                        'productName', p.name,
                        'quantity', pb.quantity
                )
                           )
        )
    FROM product_batches pb
             JOIN products p ON pb.product_id = p.id
    WHERE pb.expiry_date BETWEEN p_start_date AND p_end_date
      AND pb.status IN ('ACTIVE', 'QUARANTINED')
    GROUP BY pb.expiry_date;

    -- Insert alert events (REMOVED event_year and event_month from INSERT)
    INSERT INTO expiry_calendar_cache (
        event_date,
        event_type,
        severity,
        item_count,
        total_quantity,
        total_value,
        details
    )
    SELECT
        ea.alert_date,
        'ALERT',
        CASE
            WHEN MAX(CASE WHEN eac.severity = 'CRITICAL' THEN 1 ELSE 0 END) = 1 THEN 'CRITICAL'
            WHEN MAX(CASE WHEN eac.severity = 'WARNING' THEN 1 ELSE 0 END) = 1 THEN 'HIGH'
            ELSE 'MEDIUM'
            END,
        COUNT(*),
        SUM(ea.quantity_affected),
        0,
        JSON_OBJECT(
                'alerts', JSON_ARRAYAGG(
                JSON_OBJECT(
                        'alertId', ea.id,
                        'productName', p.name,
                        'severity', eac.severity
                )
                          )
        )
    FROM expiry_alerts ea
             JOIN products p ON ea.product_id = p.id
             JOIN expiry_alert_configs eac ON ea.config_id = eac.id
    WHERE ea.alert_date BETWEEN p_start_date AND p_end_date
      AND ea.status IN ('PENDING', 'SENT')
    GROUP BY ea.alert_date;

    -- Restore original SQL mode
    SET sql_mode = @original_sql_mode;

END$$

DELIMITER ;

-- Create event for automatic cache refresh
-- First check if event scheduler is enabled
-- SELECT @@event_scheduler;
-- If not enabled, enable it:
SET GLOBAL event_scheduler = ON;
DROP EVENT IF EXISTS refresh_calendar_cache_event;
CREATE EVENT refresh_calendar_cache_event
    ON SCHEDULE EVERY 1 HOUR
    DO CALL RefreshExpiryCalendarCache(
        CURDATE(),
        DATE_ADD(CURDATE(), INTERVAL 90 DAY)
            );

-- Initial cache population
CALL RefreshExpiryCalendarCache(
        CURDATE(),
        DATE_ADD(CURDATE(), INTERVAL 90 DAY)
     );

-- Run this SQL script to add missing indexes
-- Compatible with MySQL versions before 8.0.13

-- 1. CRITICAL: Missing index on notifications.expires_at
ALTER TABLE notifications ADD INDEX idx_notifications_expires_at (expires_at);

-- 2. Optimize notification queries
ALTER TABLE notifications ADD INDEX idx_notifications_user_status (user_id, status, created_at DESC);

-- 3. Product batch performance
ALTER TABLE product_batches ADD INDEX idx_batch_product_status (product_id, status);

-- 4. Expiry alerts optimization
ALTER TABLE expiry_alerts ADD INDEX idx_expiry_alert_batch_status (batch_id, status);

-- 5. Stock transactions history
ALTER TABLE stock_transactions ADD INDEX idx_stock_trans_product_date (product_id, transaction_date DESC);

-- 6. Quarantine lookups
ALTER TABLE quarantine_records ADD INDEX idx_quarantine_date_status (quarantine_date, status);

-- 7. Products - CORRECTED COLUMN NAMES
ALTER TABLE products ADD INDEX idx_products_code (code);      -- Changed from product_code to code
ALTER TABLE products ADD INDEX idx_products_barcode (barcode); -- Changed from barcode_number to barcode

-- 8. User lookups (if not exists)
ALTER TABLE users ADD INDEX idx_users_email (email);

-- 9. Add these indexes for better performance
ALTER TABLE product_batches ADD INDEX idx_expiry_status_quantity (expiry_date, status, quantity);
ALTER TABLE notifications ADD INDEX idx_user_created (user_id, created_at DESC);


-- =====================================================
-- Date: 2024-XX-XX
-- Feature: Supplier Management (Week 6 - Phase 1.1)
-- Developer: Week 6 Implementation
-- Status: PENDING
-- =====================================================

-- Supplier master table
CREATE TABLE IF NOT EXISTS suppliers (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         code VARCHAR(50) UNIQUE NOT NULL,
                                         name VARCHAR(200) NOT NULL,
                                         tax_id VARCHAR(50),
                                         registration_number VARCHAR(100),
                                         website VARCHAR(255),
                                         email VARCHAR(100),
                                         phone VARCHAR(50),
                                         fax VARCHAR(50),
                                         address_line1 VARCHAR(255),
                                         address_line2 VARCHAR(255),
                                         city VARCHAR(100),
                                         state VARCHAR(100),
                                         country VARCHAR(100),
                                         postal_code VARCHAR(20),
                                         status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED') DEFAULT 'ACTIVE',
                                         rating DECIMAL(3,2) DEFAULT 0.00,
                                         payment_terms VARCHAR(100),
                                         credit_limit DECIMAL(12,2),
                                         notes TEXT,
                                         created_by BIGINT,
                                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                         FOREIGN KEY (created_by) REFERENCES users(id),
                                         INDEX idx_supplier_status (status),
                                         INDEX idx_supplier_name (name)
);

-- Supplier contacts table
CREATE TABLE IF NOT EXISTS supplier_contacts (
                                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 supplier_id BIGINT NOT NULL,
                                                 name VARCHAR(100) NOT NULL,
                                                 designation VARCHAR(100),
                                                 email VARCHAR(100),
                                                 phone VARCHAR(50),
                                                 mobile VARCHAR(50),
                                                 is_primary BOOLEAN DEFAULT FALSE,
                                                 notes TEXT,
                                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
                                                 INDEX idx_supplier_contact (supplier_id)
);

-- Supplier documents table
CREATE TABLE IF NOT EXISTS supplier_documents (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  supplier_id BIGINT NOT NULL,
                                                  document_type VARCHAR(50) NOT NULL,
                                                  document_name VARCHAR(200) NOT NULL,
                                                  file_path VARCHAR(500),
                                                  file_size BIGINT,
                                                  expiry_date DATE,
                                                  uploaded_by BIGINT,
                                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                  FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
                                                  FOREIGN KEY (uploaded_by) REFERENCES users(id),
                                                  INDEX idx_supplier_document (supplier_id),
                                                  INDEX idx_document_expiry (expiry_date)
);


-- =====================================================
-- Date: 2024-XX-XX
-- Feature: Supplier Product Catalog (Week 6 - Phase 1.2)
-- Developer: Week 6 Implementation
-- Status: PENDING
-- =====================================================

-- Supplier Product Catalog table
CREATE TABLE IF NOT EXISTS supplier_products (
                                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 supplier_id BIGINT NOT NULL,
                                                 product_id BIGINT NOT NULL,
                                                 supplier_product_code VARCHAR(100),
                                                 supplier_product_name VARCHAR(200),
                                                 unit_price DECIMAL(12,2) NOT NULL,
                                                 currency VARCHAR(3) DEFAULT 'USD',
                                                 discount_percentage DECIMAL(5,2) DEFAULT 0,
                                                 bulk_discount_percentage DECIMAL(5,2) DEFAULT 0,
                                                 bulk_quantity_threshold INT DEFAULT 0,
                                                 lead_time_days INT DEFAULT 0,
                                                 min_order_quantity INT DEFAULT 1,
                                                 max_order_quantity INT,
                                                 is_preferred BOOLEAN DEFAULT FALSE,
                                                 is_active BOOLEAN DEFAULT TRUE,
                                                 last_price_update DATE,
                                                 notes TEXT,
                                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
                                                 FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
                                                 UNIQUE KEY unique_supplier_product (supplier_id, product_id),
                                                 INDEX idx_product_supplier (product_id, supplier_id),
                                                 INDEX idx_preferred (is_preferred, product_id),
                                                 INDEX idx_active_products (is_active, supplier_id)
);

-- Price History table for tracking price changes
CREATE TABLE IF NOT EXISTS supplier_product_price_history (
                                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                              supplier_product_id BIGINT NOT NULL,
                                                              old_price DECIMAL(12,2),
                                                              new_price DECIMAL(12,2) NOT NULL,
                                                              change_percentage DECIMAL(5,2),
                                                              changed_by BIGINT,
                                                              change_reason VARCHAR(255),
                                                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                              FOREIGN KEY (supplier_product_id) REFERENCES supplier_products(id) ON DELETE CASCADE,
                                                              FOREIGN KEY (changed_by) REFERENCES users(id),
                                                              INDEX idx_history_date (supplier_product_id, created_at DESC)
);

-- =====================================================
-- UPCOMING CHANGES
-- =====================================================

-- =====================================================
-- HOW TO USE THIS FILE
-- =====================================================
-- 1. When implementing a new feature:
--    - Find the relevant section in UPCOMING CHANGES
--    - Copy the SQL statements
--    - Remove the comment markers (--)
--    - Run in MySQL
--    - Move the section up and change status to APPLIED ✓
--    - Add the actual implementation date
--
-- 2. When adding new changes:
--    - Add to the appropriate week section
--    - Include detailed comments about purpose
--    - List any dependencies
--    - Keep commented until ready to apply
--
-- 3. Before committing:
--    - Ensure all APPLIED changes work
--    - Update the "Last Updated" date
--    - Update the total tables count
-- =====================================================

-- =====================================================
-- END OF SCHEMA CHANGES
-- =====================================================