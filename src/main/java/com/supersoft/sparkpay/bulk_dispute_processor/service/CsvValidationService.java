package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.springframework.web.multipart.MultipartFile;

public interface CsvValidationService {
    ValidationResult validateCsv(MultipartFile file);
    
    class ValidationResult {
        private java.util.List<String> headers = new java.util.ArrayList<>();
        private java.util.List<ValidationError> errors = new java.util.ArrayList<>();
        private java.util.List<String> warnings = new java.util.ArrayList<>();
        private java.util.Map<Integer, java.util.List<ValidationError>> rowErrors = new java.util.HashMap<>();
        private int totalRows = 0;
        private int validRows = 0;
        private int invalidRows = 0;
        private boolean formatValid = true;

        public void addError(ValidationError error) {
            errors.add(error);
            rowErrors.computeIfAbsent(error.getRow(), k -> new java.util.ArrayList<>()).add(error);
        }

        public void addError(int row, String column, String reason) {
            ValidationError error = new ValidationError(row, column, reason);
            errors.add(error);
            rowErrors.computeIfAbsent(row, k -> new java.util.ArrayList<>()).add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean isFormatValid() {
            return formatValid;
        }

        public void setFormatValid(boolean formatValid) {
            this.formatValid = formatValid;
        }

        public boolean hasAnyValidRows() {
            return validRows > 0;
        }

        public String getErrorSummary() {
            return errors.stream()
                    .map(error -> String.format("Row %d, Column '%s': %s", 
                            error.getRow(), error.getColumn(), error.getReason()))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
        }

        // Getters and setters
        public java.util.List<String> getHeaders() { return headers; }
        public void setHeaders(java.util.List<String> headers) { this.headers = headers; }
        public java.util.List<ValidationError> getErrors() { return errors; }
        public java.util.List<String> getWarnings() { return warnings; }
        public java.util.Map<Integer, java.util.List<ValidationError>> getRowErrors() { return rowErrors; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getValidRows() { return validRows; }
        public void setValidRows(int validRows) { this.validRows = validRows; }
        public int getInvalidRows() { return invalidRows; }
        public void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }
    }

    class ValidationError {
        private int row;
        private String column;
        private String reason;

        public ValidationError(int row, String column, String reason) {
            this.row = row;
            this.column = column;
            this.reason = reason;
        }

        // Getters
        public int getRow() { return row; }
        public String getColumn() { return column; }
        public String getReason() { return reason; }
    }
}
