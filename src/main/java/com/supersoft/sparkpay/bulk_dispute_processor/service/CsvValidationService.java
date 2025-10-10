package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.springframework.web.multipart.MultipartFile;

public interface CsvValidationService {
    ValidationResult validateCsv(MultipartFile file);
    
    class ValidationResult {
        private java.util.List<String> headers = new java.util.ArrayList<>();
        private java.util.List<String> errors = new java.util.ArrayList<>();
        private java.util.List<String> warnings = new java.util.ArrayList<>();
        private int totalRows = 0;
        private int validRows = 0;
        private int invalidRows = 0;

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public String getErrorSummary() {
            return String.join("; ", errors);
        }

        // Getters and setters
        public java.util.List<String> getHeaders() { return headers; }
        public void setHeaders(java.util.List<String> headers) { this.headers = headers; }
        public java.util.List<String> getErrors() { return errors; }
        public java.util.List<String> getWarnings() { return warnings; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getValidRows() { return validRows; }
        public void setValidRows(int validRows) { this.validRows = validRows; }
        public int getInvalidRows() { return invalidRows; }
        public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }
    }
}
