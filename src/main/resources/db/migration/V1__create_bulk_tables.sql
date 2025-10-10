CREATE TABLE bulk_dispute_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uploaded_by VARCHAR(255),
  file_path VARCHAR(1000) NOT NULL,
  file_name VARCHAR(255),
  status ENUM('UPLOADED','VALIDATED','PREVIEWED','CONFIRMED','PROCESSED','FAILED') NOT NULL DEFAULT 'UPLOADED',
  total_rows INT DEFAULT 0,
  valid_rows INT DEFAULT 0,
  invalid_rows INT DEFAULT 0,
  error_summary TEXT,
  version INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE bulk_dispute_job (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  job_ref VARCHAR(255) UNIQUE,
  status ENUM('PENDING','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
  total_rows INT DEFAULT 0,
  processed_rows INT DEFAULT 0,
  success_count INT DEFAULT 0,
  failure_count INT DEFAULT 0,
  error_report_path VARCHAR(1000),
  started_at DATETIME,
  completed_at DATETIME,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_job_session FOREIGN KEY (session_id) REFERENCES bulk_dispute_session(id) ON DELETE CASCADE
);

CREATE TABLE bulk_dispute_job_audit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_id BIGINT,
  action VARCHAR(255),
  message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_job FOREIGN KEY (job_id) REFERENCES bulk_dispute_job(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_status ON bulk_dispute_session(status);
