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

-- Suppliers table
CREATE TABLE IF NOT EXISTS suppliers (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         name VARCHAR(100) NOT NULL,
                                         contact_email VARCHAR(100) UNIQUE NOT NULL,
                                         phone VARCHAR(20),
                                         address TEXT,
                                         contact_person VARCHAR(100),
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
-- Last Updated: 2024-02-XX (Update with today's date)
-- Total Tables: 9 (categories, users, suppliers, products, stock_transactions, file_uploads, expiry_alert_configs, expiry_alerts, expiry_check_logs)
-- =====================================================