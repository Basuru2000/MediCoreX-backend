-- =====================================================
-- Date: 2024-01-15
-- Feature: Stock Management (Week 3)
-- Developer: Week 3 Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Stock transactions table (Week 3 addition)
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

-- Insert initial data
INSERT INTO categories (name, description) VALUES
                                               ('Medications', 'Pharmaceutical drugs and medicines'),
                                               ('Medical Supplies', 'Consumable medical supplies'),
                                               ('Equipment', 'Medical equipment and devices')
    ON DUPLICATE KEY UPDATE name=name; -- Prevents duplicate entries

-- =====================================================
-- Date: 2024-01-20
-- Feature: Product Image Support
-- Developer: Week 3-4 Implementation
-- Status: APPLIED ✓
-- =====================================================
-- Add image_url column to products table
ALTER TABLE products ADD COLUMN image_url VARCHAR(500) AFTER manufacturer;

-- Create file_uploads table for tracking uploads
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
-- Add parent_id column to categories table
ALTER TABLE categories ADD COLUMN parent_id BIGINT AFTER description;

-- Add foreign key constraint
ALTER TABLE categories
    ADD CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT;

-- Add index for better performance
CREATE INDEX idx_category_parent ON categories(parent_id);

-- =====================================================
-- Date: 2024-01-25
-- Feature: User Profile Images and Gender
-- Developer: Enhancement Implementation
-- Status: APPLIED ✓
-- =====================================================

-- Add gender and profile_image_url columns to users table
ALTER TABLE users ADD COLUMN gender VARCHAR(20) DEFAULT 'NOT_SPECIFIED' AFTER role;
-- Add profile image URL column
ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) AFTER gender;

-- Update existing users to have gender value
-- Temporarily disable safe mode
SET SQL_SAFE_UPDATES = 0;
-- Run your update
UPDATE users SET gender = 'NOT_SPECIFIED' WHERE gender IS NULL;
-- Re-enable safe mode
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
