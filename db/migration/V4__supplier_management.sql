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
-- Date: [Current Date]
-- Feature: Supplier Performance Metrics (Week 6 - Phase 1.3)
-- Developer: Week 6 Implementation
-- Status: FIXED FOR MYSQL COMPATIBILITY
-- =====================================================

-- Supplier Performance Metrics table
CREATE TABLE IF NOT EXISTS supplier_metrics (
                                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                supplier_id BIGINT NOT NULL,
                                                metric_month DATE NOT NULL,

    -- Delivery Performance Metrics
                                                total_deliveries INT DEFAULT 0,
                                                on_time_deliveries INT DEFAULT 0,
                                                late_deliveries INT DEFAULT 0,
                                                early_deliveries INT DEFAULT 0,
                                                delivery_performance_score DECIMAL(5,2) DEFAULT 0,

    -- Quality Metrics
    total_items_received INT DEFAULT 0,
    accepted_items INT DEFAULT 0,
    rejected_items INT DEFAULT 0,
    quality_score DECIMAL(5,2) DEFAULT 0,

    -- Pricing Metrics
    average_price_variance DECIMAL(5,2) DEFAULT 0,
    total_spend DECIMAL(12,2) DEFAULT 0,
    cost_savings DECIMAL(12,2) DEFAULT 0,

    -- Response Time Metrics
    average_response_time_hours INT DEFAULT 0,
    average_lead_time_days INT DEFAULT 0,

    -- Compliance Metrics
    compliance_score DECIMAL(5,2) DEFAULT 100,
    documentation_accuracy DECIMAL(5,2) DEFAULT 100,

    -- Overall Performance
    overall_score DECIMAL(5,2) DEFAULT 0,
    performance_trend ENUM('IMPROVING', 'STABLE', 'DECLINING') DEFAULT 'STABLE',

    -- Timestamps
    calculated_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Constraints
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    UNIQUE KEY unique_supplier_month (supplier_id, metric_month),
    INDEX idx_metric_month (metric_month),
    INDEX idx_overall_score (overall_score),
    INDEX idx_performance_trend (performance_trend)
    );

-- Create supplier_documents table if missing
CREATE TABLE IF NOT EXISTS supplier_documents (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  supplier_id BIGINT NOT NULL,
                                                  document_type VARCHAR(50),
    document_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    expiry_date DATE,
    uploaded_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id),
    INDEX idx_supplier_document (supplier_id),
    INDEX idx_document_expiry (expiry_date)
    );

-- Create view for current month metrics
CREATE OR REPLACE VIEW current_supplier_metrics AS
SELECT
    sm.*,
    s.name as supplier_name,
    s.code as supplier_code,
    s.status as supplier_status
FROM supplier_metrics sm
         JOIN suppliers s ON sm.supplier_id = s.id
WHERE sm.metric_month = DATE_FORMAT(CURRENT_DATE, '%Y-%m-01');

-- Create view for supplier rankings
CREATE OR REPLACE VIEW supplier_rankings AS
SELECT
    sm.supplier_id,
    s.name as supplier_name,
    sm.overall_score,
    sm.delivery_performance_score,
    sm.quality_score,
    sm.compliance_score,
    sm.performance_trend,
    RANK() OVER (ORDER BY sm.overall_score DESC) as rank_overall,
    RANK() OVER (ORDER BY sm.delivery_performance_score DESC) as rank_delivery,
    RANK() OVER (ORDER BY sm.quality_score DESC) as rank_quality
FROM supplier_metrics sm
         JOIN suppliers s ON sm.supplier_id = s.id
WHERE sm.metric_month = DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')
  AND s.status = 'ACTIVE';

-- =====================================================
-- Add rating column to suppliers table (MySQL compatible)
-- =====================================================

-- Drop procedure if exists (for re-running)
DROP PROCEDURE IF EXISTS AddColumnIfNotExists;

-- Create temporary procedure to add column if not exists
DELIMITER $$
CREATE PROCEDURE AddColumnIfNotExists()
BEGIN
    DECLARE column_exists INT DEFAULT 0;

    -- Check if column exists
SELECT COUNT(*) INTO column_exists
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'suppliers'
  AND COLUMN_NAME = 'rating';

-- Add column if it doesn't exist
IF column_exists = 0 THEN
ALTER TABLE suppliers
    ADD COLUMN rating DECIMAL(3,2) DEFAULT 0 COMMENT 'Overall rating from 0-5 based on metrics';
END IF;
END$$
DELIMITER ;

-- Execute the procedure
CALL AddColumnIfNotExists();

-- Drop the temporary procedure
DROP PROCEDURE IF EXISTS AddColumnIfNotExists;

-- =====================================================
-- Create index for rating (MySQL compatible)
-- =====================================================

-- Drop procedure if exists
DROP PROCEDURE IF EXISTS AddIndexIfNotExists;

-- Create temporary procedure to add index if not exists
DELIMITER $$
CREATE PROCEDURE AddIndexIfNotExists()
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    -- Check if index exists
SELECT COUNT(*) INTO index_exists
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'suppliers'
  AND INDEX_NAME = 'idx_supplier_rating';

-- Add index if it doesn't exist
IF index_exists = 0 THEN
CREATE INDEX idx_supplier_rating ON suppliers(rating);
END IF;
END$$
DELIMITER ;

-- Execute the procedure
CALL AddIndexIfNotExists();

-- Drop the temporary procedure
DROP PROCEDURE IF EXISTS AddIndexIfNotExists;

-- =====================================================
-- Create stored procedure to calculate monthly metrics
-- =====================================================

-- Drop existing procedure if exists
DROP PROCEDURE IF EXISTS calculate_supplier_monthly_metrics;

DELIMITER $$
CREATE PROCEDURE calculate_supplier_monthly_metrics(
    IN p_supplier_id BIGINT,
    IN p_month DATE
)
BEGIN
    DECLARE v_total_deliveries INT DEFAULT 0;
    DECLARE v_on_time INT DEFAULT 0;
    DECLARE v_total_items INT DEFAULT 0;
    DECLARE v_accepted_items INT DEFAULT 0;
    DECLARE v_total_spend DECIMAL(12,2) DEFAULT 0;
    DECLARE v_overall_score DECIMAL(5,2) DEFAULT 0;

    -- Set month to first day
    SET p_month = DATE_FORMAT(p_month, '%Y-%m-01');

    -- Calculate overall score
    SET v_overall_score = (
        IFNULL((SELECT delivery_performance_score FROM supplier_metrics
                WHERE supplier_id = p_supplier_id AND metric_month = p_month), 0) * 0.30 +
        IFNULL((SELECT quality_score FROM supplier_metrics
                WHERE supplier_id = p_supplier_id AND metric_month = p_month), 0) * 0.35 +
        IFNULL((SELECT compliance_score FROM supplier_metrics
                WHERE supplier_id = p_supplier_id AND metric_month = p_month), 100) * 0.20 +
        (100 - ABS(IFNULL((SELECT average_price_variance FROM supplier_metrics
                           WHERE supplier_id = p_supplier_id AND metric_month = p_month), 0))) * 0.15
        );

    -- Update or insert metrics
INSERT INTO supplier_metrics (
    supplier_id, metric_month, overall_score, calculated_at
) VALUES (
             p_supplier_id, p_month, v_overall_score, NOW()
         ) ON DUPLICATE KEY UPDATE
    overall_score = v_overall_score,
    calculated_at = NOW(),
    updated_at = NOW();

-- Update supplier rating (0-5 scale)
UPDATE suppliers
SET rating = v_overall_score / 20
WHERE id = p_supplier_id;

END$$
DELIMITER ;

-- =====================================================
-- Create trigger to update performance trend
-- =====================================================

-- Drop existing trigger if exists
DROP TRIGGER IF EXISTS update_performance_trend;

DELIMITER $$
CREATE TRIGGER update_performance_trend
    BEFORE UPDATE ON supplier_metrics
    FOR EACH ROW
BEGIN
    DECLARE v_previous_score DECIMAL(5,2);

    -- Get previous month's score
    SELECT overall_score INTO v_previous_score
    FROM supplier_metrics
    WHERE supplier_id = NEW.supplier_id
      AND metric_month = DATE_SUB(NEW.metric_month, INTERVAL 1 MONTH)
        LIMIT 1;

    -- Set trend based on score change
    IF v_previous_score IS NOT NULL THEN
        IF NEW.overall_score > v_previous_score + 5 THEN
            SET NEW.performance_trend = 'IMPROVING';
        ELSEIF NEW.overall_score < v_previous_score - 5 THEN
            SET NEW.performance_trend = 'DECLINING';
    ELSE
            SET NEW.performance_trend = 'STABLE';
END IF;
END IF;
END$$
DELIMITER ;

-- =====================================================
-- Insert sample test data
-- =====================================================

-- Insert test metrics for existing suppliers
INSERT INTO supplier_metrics (
    supplier_id,
    metric_month,
    total_deliveries,
    on_time_deliveries,
    delivery_performance_score,
    total_items_received,
    accepted_items,
    quality_score,
    compliance_score,
    overall_score,
    total_spend,
    cost_savings,
    average_price_variance,
    calculated_at
)
SELECT
    s.id,
    DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'),
    10,
    8,
    80.00,
    100,
    95,
    95.00,
    100.00,
    91.25,
    50000.00,
    1000.00,
    -2.5,
    NOW()
FROM suppliers s
WHERE s.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM supplier_metrics sm
    WHERE sm.supplier_id = s.id
      AND sm.metric_month = DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')
)
    LIMIT 1;

-- Add historical data for testing (3 months back)
INSERT INTO supplier_metrics (
    supplier_id,
    metric_month,
    total_deliveries,
    on_time_deliveries,
    delivery_performance_score,
    total_items_received,
    accepted_items,
    quality_score,
    compliance_score,
    overall_score,
    performance_trend,
    total_spend,
    calculated_at
)
SELECT
    s.id,
    DATE_FORMAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH), '%Y-%m-01'),
    12,
    10,
    83.33,
    120,
    110,
    91.67,
    100.00,
    90.00,
    'STABLE',
    60000.00,
    NOW()
FROM suppliers s
WHERE s.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM supplier_metrics sm
    WHERE sm.supplier_id = s.id
      AND sm.metric_month = DATE_FORMAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH), '%Y-%m-01')
)
    LIMIT 1;

-- 1. Disable safe mode temporarily
SET SQL_SAFE_UPDATES = 0;

-- 2. Update the ratings
UPDATE suppliers s
SET rating = IFNULL((
    SELECT overall_score / 20
    FROM supplier_metrics sm
    WHERE sm.supplier_id = s.id
    ORDER BY metric_month DESC
    LIMIT 1
    ), 0)
WHERE id IN (SELECT DISTINCT supplier_id FROM supplier_metrics);

-- 3. Re-enable safe mode
SET SQL_SAFE_UPDATES = 1;

-- =====================================================
-- Date: 2024-XX-XX
-- Feature: Document Management Indexes and Updates
-- Developer: Phase 1.4 Implementation
-- Status: ACTIVE
-- =====================================================

-- Add indexes for better document query performance
ALTER TABLE supplier_documents ADD INDEX idx_expiry_date (expiry_date);
ALTER TABLE supplier_documents ADD INDEX idx_document_type (document_type);
ALTER TABLE supplier_documents ADD INDEX idx_created_at (created_at DESC);

-- Add document statistics view
CREATE OR REPLACE VIEW supplier_document_stats AS
SELECT
    s.id as supplier_id,
    s.name as supplier_name,
    COUNT(DISTINCT sd.id) as total_documents,
    COUNT(DISTINCT CASE WHEN sd.expiry_date < CURRENT_DATE THEN sd.id END) as expired_documents,
    COUNT(DISTINCT CASE WHEN sd.expiry_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY) THEN sd.id END) as expiring_soon
FROM suppliers s
         LEFT JOIN supplier_documents sd ON s.id = sd.supplier_id
GROUP BY s.id, s.name;


-- =====================================================
-- Date: 2025-01-XX (Update with actual date)
-- Feature: Purchase Order System - Phase 2.1 Basic PO Creation
-- Developer: Phase 2.1 Implementation
-- Status: ACTIVE
-- =====================================================

-- Purchase Orders table
CREATE TABLE IF NOT EXISTS purchase_orders (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               po_number VARCHAR(50) UNIQUE NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expected_delivery_date DATE,
    status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
    subtotal DECIMAL(12,2) DEFAULT 0,
    tax_amount DECIMAL(12,2) DEFAULT 0,
    discount_amount DECIMAL(12,2) DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    notes TEXT,
    created_by BIGINT NOT NULL,
    approved_by BIGINT,
    approved_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (approved_by) REFERENCES users(id),

    INDEX idx_po_number (po_number),
    INDEX idx_po_supplier (supplier_id),
    INDEX idx_po_status (status),
    INDEX idx_po_order_date (order_date DESC),
    INDEX idx_po_created_by (created_by)
    );

-- Purchase Order Lines table
CREATE TABLE IF NOT EXISTS purchase_order_lines (
                                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                    po_id BIGINT NOT NULL,
                                                    product_id BIGINT NOT NULL,
                                                    supplier_product_id BIGINT,
                                                    product_name VARCHAR(200) NOT NULL,
    product_code VARCHAR(50),
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    discount_percentage DECIMAL(5,2) DEFAULT 0,
    tax_percentage DECIMAL(5,2) DEFAULT 0,
    line_total DECIMAL(12,2) NOT NULL,
    received_quantity INT DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (supplier_product_id) REFERENCES supplier_products(id),

    INDEX idx_po_line_po (po_id),
    INDEX idx_po_line_product (product_id)
    );

-- Create view for PO summary statistics
CREATE OR REPLACE VIEW purchase_order_summary AS
SELECT
    po.id,
    po.po_number,
    po.status,
    COUNT(pol.id) as line_items_count,
    SUM(pol.quantity) as total_items,
    SUM(pol.received_quantity) as total_received,
    po.total_amount
FROM purchase_orders po
         LEFT JOIN purchase_order_lines pol ON po.id = pol.po_id
GROUP BY po.id, po.po_number, po.status, po.total_amount;

-- =====================================================
-- END OF PURCHASE ORDER SCHEMA CHANGES
-- =====================================================


-- =====================================================
-- Date: 2025-01-02
-- Feature: Purchase Order Approval Process - Phase 2.2
-- Developer: Phase 2.2 Implementation
-- Status: ACTIVE
-- =====================================================

-- Add rejection comments field to purchase_orders table
ALTER TABLE purchase_orders
    ADD COLUMN rejection_comments TEXT AFTER approved_date;

-- Add index for better query performance on approval status
CREATE INDEX idx_po_approval_status ON purchase_orders(status, approved_by);

-- Create view for pending approvals (for quick manager dashboard)
CREATE OR REPLACE VIEW pending_po_approvals AS
SELECT
    po.id,
    po.po_number,
    po.supplier_id,
    s.name as supplier_name,
    po.order_date,
    po.total_amount,
    po.created_by,
    u.full_name as created_by_name,
    po.created_at,
    COUNT(pol.id) as line_items_count
FROM purchase_orders po
         INNER JOIN suppliers s ON po.supplier_id = s.id
         INNER JOIN users u ON po.created_by = u.id
         LEFT JOIN purchase_order_lines pol ON po.id = pol.po_id
WHERE po.status = 'DRAFT'
GROUP BY po.id, po.po_number, po.supplier_id, s.name, po.order_date,
         po.total_amount, po.created_by, u.full_name, po.created_at;

-- =====================================================
-- END OF APPROVAL PROCESS SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Notification Templates for PO Approval Process
-- =====================================================

-- Template for approval request
INSERT INTO notification_templates (code, title_template, message_template, category, priority, active)
VALUES
    ('PO_APPROVAL_REQUEST',
     'Purchase Order Approval Required: {{poNumber}}',
     'A new purchase order {{poNumber}} from {{supplierName}} worth ${{amount}} has been created by {{createdBy}} and requires your approval.',
     'PROCUREMENT',
     'HIGH',
     true),

    ('PO_APPROVED',
     'Purchase Order Approved: {{poNumber}}',
     'Your purchase order {{poNumber}} has been approved by {{approverName}}. {{#comments}}Comments: {{comments}}{{/comments}}',
     'PROCUREMENT',
     'MEDIUM',
     true),

    ('PO_REJECTED',
     'Purchase Order Rejected: {{poNumber}}',
     'Your purchase order {{poNumber}} has been rejected by {{approverName}}. Reason: {{comments}}',
     'PROCUREMENT',
     'HIGH',
     true)
    ON DUPLICATE KEY UPDATE
                         title_template = VALUES(title_template),
                         message_template = VALUES(message_template);


-- =====================================================
-- Date: 2025-01-03
-- Feature: Purchase Order Status Tracking - Phase 2.3
-- Developer: Phase 2.3 Implementation
-- Status: ACTIVE
-- =====================================================

-- Purchase Order Status History table
CREATE TABLE IF NOT EXISTS purchase_order_status_history (
                                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                             po_id BIGINT NOT NULL,
                                                             po_number VARCHAR(50) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_by_name VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments TEXT,

    -- Indexes for performance
    INDEX idx_po_id (po_id),
    INDEX idx_po_number (po_number),
    INDEX idx_changed_at (changed_at DESC),
    INDEX idx_new_status (new_status),

    -- Foreign key constraints
    FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by) REFERENCES users(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create view for recent status changes
CREATE OR REPLACE VIEW recent_po_status_changes AS
SELECT
    posh.id,
    posh.po_id,
    posh.po_number,
    posh.old_status,
    posh.new_status,
    posh.changed_by,
    posh.changed_by_name,
    posh.changed_at,
    posh.comments,
    po.supplier_id,
    s.name as supplier_name
FROM purchase_order_status_history posh
         INNER JOIN purchase_orders po ON posh.po_id = po.id
         INNER JOIN suppliers s ON po.supplier_id = s.id
ORDER BY posh.changed_at DESC
    LIMIT 100;

-- Add comment to table
ALTER TABLE purchase_order_status_history
    COMMENT = 'Tracks all status changes for purchase orders with full audit trail';

-- =====================================================
-- END OF STATUS TRACKING SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Date: 2025-01-04
-- Feature: Basic Goods Receipt - Phase 3.1
-- Developer: Phase 3.1 Implementation
-- Status: ACTIVE
-- Description: Goods receipt processing with inventory integration
-- =====================================================

-- Goods Receipts table
CREATE TABLE IF NOT EXISTS goods_receipts (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              receipt_number VARCHAR(50) UNIQUE NOT NULL,
    po_id BIGINT NOT NULL,
    po_number VARCHAR(50) NOT NULL,

    -- Receipt details
    receipt_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by BIGINT NOT NULL,

    -- Supplier information (denormalized for quick access)
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,

    -- Status and notes
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    notes TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Foreign keys
    FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    FOREIGN KEY (received_by) REFERENCES users(id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),

    -- Indexes for performance
    INDEX idx_receipt_number (receipt_number),
    INDEX idx_po_id (po_id),
    INDEX idx_receipt_date (receipt_date DESC),
    INDEX idx_received_by (received_by),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Goods Receipt Lines table
CREATE TABLE IF NOT EXISTS goods_receipt_lines (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   receipt_id BIGINT NOT NULL,
                                                   po_line_id BIGINT NOT NULL,

    -- Product information (denormalized)
                                                   product_id BIGINT NOT NULL,
                                                   product_name VARCHAR(200) NOT NULL,
    product_code VARCHAR(50),

    -- Quantity details
    ordered_quantity INT NOT NULL,
    received_quantity INT NOT NULL,

    -- Batch information
    batch_id BIGINT,
    batch_number VARCHAR(50) NOT NULL,
    expiry_date DATE NOT NULL,
    manufacture_date DATE,

    -- Cost information
    unit_cost DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,

    -- Quality notes
    quality_notes TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign keys
    FOREIGN KEY (receipt_id) REFERENCES goods_receipts(id) ON DELETE CASCADE,
    FOREIGN KEY (po_line_id) REFERENCES purchase_order_lines(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (batch_id) REFERENCES product_batches(id),

    -- Indexes
    INDEX idx_receipt_id (receipt_id),
    INDEX idx_po_line_id (po_line_id),
    INDEX idx_product_id (product_id),
    INDEX idx_batch_id (batch_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create view for receipt summary
CREATE OR REPLACE VIEW goods_receipt_summary AS
SELECT
    gr.id,
    gr.receipt_number,
    gr.po_number,
    gr.receipt_date,
    gr.supplier_name,
    u.full_name as received_by_name,
    gr.status,
    COUNT(grl.id) as line_count,
    SUM(grl.received_quantity) as total_quantity,
    SUM(grl.line_total) as total_value
FROM goods_receipts gr
         LEFT JOIN goods_receipt_lines grl ON gr.id = grl.receipt_id
         LEFT JOIN users u ON gr.received_by = u.id
GROUP BY gr.id, gr.receipt_number, gr.po_number, gr.receipt_date,
         gr.supplier_name, u.full_name, gr.status;

-- Notification templates for receiving
INSERT INTO notification_templates (code, title_template, message_template, category, priority, active)
VALUES
    ('GOODS_RECEIVED',
     'Goods Receipt Processed: {{receiptNumber}}',
     'Goods receipt {{receiptNumber}} for PO {{poNumber}} has been processed. {{totalQuantity}} items received from {{supplierName}}.',
     'PROCUREMENT',
     'MEDIUM',
     true),

    ('PO_FULLY_RECEIVED',
     'Purchase Order Fully Received: {{poNumber}}',
     'All items for purchase order {{poNumber}} have been received and added to inventory.',
     'PROCUREMENT',
     'MEDIUM',
     true)
    ON DUPLICATE KEY UPDATE
                         title_template = VALUES(title_template),
                         message_template = VALUES(message_template);

-- =====================================================
-- END OF GOODS RECEIPT SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Date: 2025-01-04
-- Feature: Simple Accept/Reject Decision - Phase 3.2
-- Status: READY TO APPLY
-- Description: Add quality control decision workflow to goods receipts
-- =====================================================

-- Add acceptance status and quality control fields to goods_receipts
ALTER TABLE goods_receipts
    ADD COLUMN acceptance_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL' AFTER status,
    ADD COLUMN rejection_reason TEXT AFTER acceptance_status,
    ADD COLUMN quality_checked_by BIGINT AFTER rejection_reason,
    ADD COLUMN quality_checked_at TIMESTAMP NULL AFTER quality_checked_by,
    ADD CONSTRAINT fk_quality_checked_by FOREIGN KEY (quality_checked_by) REFERENCES users(id);

-- Add index for faster queries on acceptance status
CREATE INDEX idx_acceptance_status ON goods_receipts(acceptance_status);

-- Update existing receipts to ACCEPTED status (backward compatibility)
UPDATE goods_receipts
SET acceptance_status = 'ACCEPTED',
    quality_checked_at = created_at,
    quality_checked_by = received_by
WHERE acceptance_status = 'PENDING_APPROVAL';

-- Add notification templates for quality decisions
INSERT INTO notification_templates (code, title_template, message_template, category, priority, active)
VALUES
    ('GOODS_RECEIPT_ACCEPTED',
     'Goods Receipt Accepted: {{receiptNumber}}',
     'Goods receipt {{receiptNumber}} for PO {{poNumber}} has been accepted. {{totalQuantity}} items added to inventory.',
     'PROCUREMENT',
     'MEDIUM',
     true),

    ('GOODS_RECEIPT_REJECTED',
     'Goods Receipt Rejected: {{receiptNumber}}',
     'Goods receipt {{receiptNumber}} for PO {{poNumber}} has been rejected. Reason: {{rejectionReason}}',
     'PROCUREMENT',
     'HIGH',
     true)
    ON DUPLICATE KEY UPDATE
                         title_template = VALUES(title_template),
                         message_template = VALUES(message_template);

-- =====================================================
-- END OF PHASE 3.2 SCHEMA CHANGES
-- =====================================================


-- =====================================================
-- Date: 2025-01-05
-- Feature: Partial Receipt Handling - Phase 4.1
-- Status: READY TO APPLY
-- Description: Enable multiple receipts per PO with remaining quantity tracking
-- =====================================================

-- Add remaining_quantity field to purchase_order_lines
ALTER TABLE purchase_order_lines
    ADD COLUMN remaining_quantity INT NOT NULL DEFAULT 0 AFTER received_quantity;

-- Run your update
UPDATE purchase_order_lines
SET remaining_quantity = quantity - received_quantity;

-- Add index for efficient queries on remaining quantities
CREATE INDEX idx_remaining_quantity ON purchase_order_lines(remaining_quantity);

-- ADD THIS: Update status ENUM to include PARTIALLY_RECEIVED
ALTER TABLE purchase_orders
    MODIFY COLUMN status ENUM(
    'DRAFT',
    'APPROVED',
    'SENT',
    'PARTIALLY_RECEIVED',
    'RECEIVED',
    'CANCELLED'
    ) NOT NULL DEFAULT 'DRAFT';

-- Add comment to table
ALTER TABLE purchase_order_lines
    COMMENT = 'Purchase order line items with partial receipt tracking';

-- Create view for PO fulfillment status
CREATE OR REPLACE VIEW po_fulfillment_status AS
SELECT
    po.id AS po_id,
    po.po_number,
    po.status,
    COUNT(pol.id) AS total_lines,
    SUM(pol.quantity) AS total_ordered,
    SUM(pol.received_quantity) AS total_received,
    SUM(pol.remaining_quantity) AS total_remaining,
    ROUND((SUM(pol.received_quantity) / SUM(pol.quantity)) * 100, 2) AS fulfillment_percentage
FROM purchase_orders po
         LEFT JOIN purchase_order_lines pol ON po.id = pol.po_id
GROUP BY po.id, po.po_number, po.status;

-- Add notification template for partial receipt
INSERT INTO notification_templates (code, title_template, message_template, category, priority, active)
VALUES
    ('PO_PARTIALLY_RECEIVED',
     'Partial Receipt Processed: {{poNumber}}',
     'Partial receipt {{receiptNumber}} for PO {{poNumber}} has been processed. {{receivedQuantity}} of {{totalQuantity}} items received ({{percentage}}% complete).',
     'PROCUREMENT',
     'MEDIUM',
     true)
    ON DUPLICATE KEY UPDATE
                         title_template = VALUES(title_template),
                         message_template = VALUES(message_template);

-- =====================================================
-- END OF PARTIAL RECEIPT HANDLING SCHEMA CHANGES
-- =====================================================

-- =====================================================
-- Date: 2025-01-06
-- Feature: Quality Inspection Checklist - Phase 4.2
-- Status: READY TO APPLY
-- Description: Structured quality control process with audit trail
-- =====================================================

-- 1. Quality Checklist Templates table
CREATE TABLE IF NOT EXISTS quality_checklist_templates (
                                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                           name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_category (category),
    INDEX idx_is_active (is_active),
    INDEX idx_is_default (is_default)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Quality Check Items table
CREATE TABLE IF NOT EXISTS quality_check_items (
                                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                   template_id BIGINT NOT NULL,
                                                   item_order INT NOT NULL,
                                                   check_description VARCHAR(500) NOT NULL,
    check_type VARCHAR(20) NOT NULL DEFAULT 'YES_NO',
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    expected_value VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (template_id) REFERENCES quality_checklist_templates(id) ON DELETE CASCADE,
    INDEX idx_template_id (template_id),
    INDEX idx_item_order (item_order),
    INDEX idx_is_mandatory (is_mandatory)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Goods Receipt Checklists table
CREATE TABLE IF NOT EXISTS goods_receipt_checklists (
                                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                        receipt_id BIGINT NOT NULL,
                                                        template_id BIGINT NOT NULL,
                                                        template_name VARCHAR(100) NOT NULL,
    completed_by BIGINT NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    overall_result VARCHAR(20) NOT NULL,
    inspector_notes TEXT,

    FOREIGN KEY (receipt_id) REFERENCES goods_receipts(id) ON DELETE CASCADE,
    FOREIGN KEY (template_id) REFERENCES quality_checklist_templates(id),
    FOREIGN KEY (completed_by) REFERENCES users(id),
    INDEX idx_receipt_id (receipt_id),
    INDEX idx_completed_by (completed_by),
    INDEX idx_overall_result (overall_result)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Checklist Answers table
CREATE TABLE IF NOT EXISTS checklist_answers (
                                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 checklist_id BIGINT NOT NULL,
                                                 check_item_id BIGINT NOT NULL,
                                                 check_description VARCHAR(500) NOT NULL,
    answer VARCHAR(100) NOT NULL,
    is_compliant BOOLEAN NOT NULL DEFAULT TRUE,
    remarks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (checklist_id) REFERENCES goods_receipt_checklists(id) ON DELETE CASCADE,
    FOREIGN KEY (check_item_id) REFERENCES quality_check_items(id),
    INDEX idx_checklist_id (checklist_id),
    INDEX idx_is_compliant (is_compliant)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Insert default quality checklist template
INSERT INTO quality_checklist_templates (name, description, category, is_active, is_default)
VALUES
    ('Standard Medical Supplies QC',
     'Standard quality checklist for medical supplies and equipment',
     'GENERAL',
     TRUE,
     TRUE);

-- 6. Insert default quality check items
INSERT INTO quality_check_items (template_id, item_order, check_description, check_type, is_mandatory, expected_value)
VALUES
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
    1, 'Package integrity - no damage or tampering', 'YES_NO', TRUE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     2, 'Product labels are clear and legible', 'YES_NO', TRUE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     3, 'Batch number matches purchase order', 'YES_NO', TRUE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     4, 'Expiry date is acceptable (>6 months)', 'YES_NO', TRUE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     5, 'Temperature-sensitive items maintained correctly', 'YES_NO', FALSE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     6, 'Quantity matches purchase order', 'YES_NO', TRUE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     7, 'Product appearance is normal (color, consistency)', 'YES_NO', TRUE, 'YES'),
    ((SELECT id FROM quality_checklist_templates WHERE is_default = TRUE LIMIT 1),
     8, 'Sterile items - packaging intact', 'YES_NO', FALSE, 'YES');

-- =====================================================
-- END OF QUALITY INSPECTION CHECKLIST SCHEMA CHANGES
-- =====================================================


-- =====================================================
-- Date: 2025-01-07
-- Feature: Automated PO Generation - Phase 4.3
-- Status: READY TO APPLY
-- Description: Enable automated purchase order creation for low-stock products
-- =====================================================

-- 1. Add auto_generated flag to purchase_orders table
ALTER TABLE purchase_orders
    ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE AFTER rejection_comments,
    ADD INDEX idx_auto_generated (auto_generated);

-- 2. Create Auto PO Configuration table
CREATE TABLE IF NOT EXISTS auto_po_configuration (
                                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                     enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                                     schedule_cron VARCHAR(50) NOT NULL DEFAULT '0 0 2 * * ?', -- Run at 2 AM daily
    reorder_multiplier DECIMAL(3,2) NOT NULL DEFAULT 2.00,  -- Order 2x min stock
    days_until_delivery INT NOT NULL DEFAULT 7,  -- Expected delivery in 7 days
    min_po_value DECIMAL(12,2) DEFAULT 100.00,  -- Minimum PO value to generate
    only_preferred_suppliers BOOLEAN NOT NULL DEFAULT TRUE,
    auto_approve BOOLEAN NOT NULL DEFAULT FALSE,  -- Keep in DRAFT for review
    notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notify_roles VARCHAR(200) DEFAULT 'HOSPITAL_MANAGER,PROCUREMENT_OFFICER',
    last_run_at TIMESTAMP NULL,
    last_run_status VARCHAR(20) NULL,  -- SUCCESS, FAILED, PARTIAL
    last_run_details TEXT NULL,  -- JSON with generation details
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CHECK (reorder_multiplier >= 1.0 AND reorder_multiplier <= 10.0),
    CHECK (days_until_delivery >= 1 AND days_until_delivery <= 90)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Insert default configuration
INSERT INTO auto_po_configuration (
    enabled,
    schedule_cron,
    reorder_multiplier,
    days_until_delivery,
    min_po_value,
    only_preferred_suppliers,
    auto_approve,
    notification_enabled,
    notify_roles
) VALUES (
             TRUE,                      -- enabled
             '0 0 2 * * ?',            -- 2 AM daily
             2.00,                      -- Order 2x min stock
             7,                         -- 7 days delivery
             100.00,                    -- Min $100 PO value
             TRUE,                      -- Only preferred suppliers
             FALSE,                     -- Keep as DRAFT
             TRUE,                      -- Send notifications
             'HOSPITAL_MANAGER,PROCUREMENT_OFFICER'  -- Notify these roles
         );

-- 4. Add notification templates for auto-generated POs
INSERT INTO notification_templates (code, title_template, message_template, category, priority, active)
VALUES
    ('AUTO_PO_GENERATED',
     'Auto-Generated PO Created: {{poNumber}}',
     'System automatically created purchase order {{poNumber}} for {{itemCount}} low-stock items. Total value: ${{totalAmount}}. Please review and approve.',
     'PROCUREMENT',
     'HIGH',
     true),

    ('AUTO_PO_GENERATION_FAILED',
     'Auto PO Generation Failed',
     'Automated PO generation job failed. Reason: {{failureReason}}. Please check configuration and try again.',
     'SYSTEM',
     'HIGH',
     true),

    ('AUTO_PO_BATCH_SUMMARY',
     'Auto PO Daily Summary',
     'Generated {{poCount}} purchase orders for {{productCount}} low-stock products. Total value: ${{totalValue}}.',
     'PROCUREMENT',
     'MEDIUM',
     true)
    ON DUPLICATE KEY UPDATE
                         title_template = VALUES(title_template),
                         message_template = VALUES(message_template);

-- 5. Create index for low-stock product queries
CREATE INDEX idx_products_low_stock ON products(quantity, min_stock);

-- =====================================================
-- END OF AUTO PO GENERATION SCHEMA CHANGES
-- =====================================================


-- =====================================================
-- PHASE 4.4: PO ANALYTICS DASHBOARD VIEWS
-- =====================================================
-- =====================================================
-- View 1: Purchase Order Analytics Summary View
-- =====================================================
CREATE OR REPLACE VIEW purchase_order_analytics_view AS
SELECT
    po.id,
    po.po_number,
    po.order_date,
    po.expected_delivery_date,
    po.status,
    po.total_amount,
    po.approved_date,
    po.created_at,
    s.id as supplier_id,
    s.name as supplier_name,
    s.code as supplier_code,
    TIMESTAMPDIFF(HOUR, po.created_at, po.approved_date) as approval_time_hours,
    (SELECT COUNT(*) FROM purchase_order_lines WHERE po_id = po.id) as line_items_count,
    (SELECT SUM(quantity) FROM purchase_order_lines WHERE po_id = po.id) as total_items,
    (SELECT SUM(received_quantity) FROM purchase_order_lines WHERE po_id = po.id) as total_received,
    CASE
        WHEN po.status = 'RECEIVED' THEN 100
        WHEN (SELECT SUM(quantity) FROM purchase_order_lines WHERE po_id = po.id) > 0
            THEN ((SELECT SUM(received_quantity) FROM purchase_order_lines WHERE po_id = po.id) * 100.0 /
                  (SELECT SUM(quantity) FROM purchase_order_lines WHERE po_id = po.id))
        ELSE 0
        END as fulfillment_percentage,
    po.auto_generated
FROM purchase_orders po
         INNER JOIN suppliers s ON po.supplier_id = s.id;

-- =====================================================
-- View 2: Monthly PO Trends
-- =====================================================
CREATE OR REPLACE VIEW po_monthly_trends AS
SELECT
    DATE_FORMAT(order_date, '%Y-%m') as month,
    COUNT(*) as po_count,
    SUM(total_amount) as total_value,
    AVG(total_amount) as avg_po_value,
    SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) as approved_count,
    SUM(CASE WHEN status = 'RECEIVED' THEN 1 ELSE 0 END) as received_count,
    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_count,
    AVG(TIMESTAMPDIFF(HOUR, created_at, approved_date)) as avg_approval_time_hours
FROM purchase_orders
WHERE order_date >= DATE_SUB(CURRENT_DATE, INTERVAL 12 MONTH)
GROUP BY DATE_FORMAT(order_date, '%Y-%m')
ORDER BY month DESC;

-- =====================================================
-- View 3: Supplier Performance Analytics
-- =====================================================
CREATE OR REPLACE VIEW supplier_performance_analytics AS
SELECT
    s.id as supplier_id,
    s.name as supplier_name,
    s.code as supplier_code,
    COUNT(po.id) as total_pos,
    SUM(po.total_amount) as total_value,
    AVG(po.total_amount) as avg_po_value,
    SUM(CASE WHEN po.status = 'RECEIVED' THEN 1 ELSE 0 END) as completed_pos,
    SUM(CASE WHEN po.status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_pos,
    CASE
        WHEN COUNT(po.id) > 0
            THEN (SUM(CASE WHEN po.status = 'RECEIVED' THEN 1 ELSE 0 END) * 100.0 / COUNT(po.id))
        ELSE 0
        END as completion_rate,
    AVG(TIMESTAMPDIFF(HOUR, po.created_at, po.approved_date)) as avg_approval_time_hours,
    MAX(po.order_date) as last_order_date
FROM suppliers s
         LEFT JOIN purchase_orders po ON s.id = po.supplier_id
GROUP BY s.id, s.name, s.code;

-- =====================================================
-- View 4: PO Status Distribution
-- =====================================================
CREATE OR REPLACE VIEW po_status_distribution AS
SELECT
    status,
    COUNT(*) as count,
    SUM(total_amount) as total_value,
    AVG(total_amount) as avg_value
FROM purchase_orders
GROUP BY status;

-- =====================================================
-- Indexes for Performance Optimization
-- =====================================================
CREATE INDEX idx_po_order_date_month ON purchase_orders(order_date);
CREATE INDEX idx_po_created_approved ON purchase_orders(created_at, approved_date);
CREATE INDEX idx_po_supplier_status ON purchase_orders(supplier_id, status);


-- =====================================================
-- UPCOMING CHANGES
-- =====================================================