-- create_database_schema.sql
-- Manual database schema creation script
-- Run this script in MySQL to create the complete bulk dispute processing system

-- Create database if it doesn't exist
CREATE DATABASE IF NOT EXISTS bulk_dispute_db;
USE bulk_dispute_db;

-- Create bulk_dispute_session table
CREATE TABLE bulk_dispute_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_code VARCHAR(50) NOT NULL,
    merchant_id VARCHAR(50) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    status ENUM('UPLOADED','VALIDATED','PREVIEWED','CONFIRMED') NOT NULL DEFAULT 'UPLOADED',
    uploaded_by VARCHAR(100) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_institution_code (institution_code),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_status (status),
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_created_at (created_at)
);

-- Create bulk_dispute_job table
CREATE TABLE bulk_dispute_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    job_ref VARCHAR(100) NOT NULL,
    status ENUM('PENDING','RUNNING','PAUSED','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    total_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    last_processed_row INT DEFAULT 0,
    error_report_path VARCHAR(500),
    retry_count INT DEFAULT 0,
    failure_reason TEXT,
    failure_type VARCHAR(50),
    last_retry_at DATETIME,
    next_retry_at DATETIME,
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES bulk_dispute_session(id) ON DELETE CASCADE,
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_job_ref (job_ref),
    INDEX idx_next_retry_at (next_retry_at),
    INDEX idx_failure_type (failure_type)
);

-- Create bulk_dispute_job_audit table
CREATE TABLE bulk_dispute_job_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    message TEXT,
    retry_count INT DEFAULT 0,
    failure_type VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES bulk_dispute_job(id) ON DELETE CASCADE,
    INDEX idx_job_id (job_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
);

-- Show tables created
SHOW TABLES;

