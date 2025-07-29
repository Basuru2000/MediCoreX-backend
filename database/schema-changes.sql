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