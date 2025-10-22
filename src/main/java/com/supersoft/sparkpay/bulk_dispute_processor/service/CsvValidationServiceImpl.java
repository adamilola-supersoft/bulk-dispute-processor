package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.config.ValidationConstants;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Slf4j
@Service
public class CsvValidationServiceImpl implements CsvValidationService {

    @Autowired
    private ProofService proofService;

    public ValidationResult validateCsv(MultipartFile file) {
        ValidationResult result = new ValidationResult();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.addError(0, "FILE", "File is empty");
                result.setFormatValid(false);
                return result;
            }
            
            List<String> headers = CsvParser.parseCsvLine(headerLine);
            // Ensure all headers are properly trimmed
            headers = headers.stream()
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toList());
            result.setHeaders(headers);
            
            // Format validation - check CSV structure and required columns
            if (!validateFormat(headers, result)) {
                result.setFormatValid(false);
                return result;
            }
            
            // Business validation - check data rules
            validateBusinessRules(file, result);
            
        } catch (IOException e) {
            log.error("Error reading CSV file", e);
            result.addError(0, "FILE", "Error reading file: " + e.getMessage());
            result.setFormatValid(false);
        }
        
        return result;
    }

    /**
     * Format validation - check CSV structure and required columns
     */
    private boolean validateFormat(List<String> headers, ValidationResult result) {
        log.info("Format validation - Parsed headers: {}", headers);
        log.info("Required columns: {}", ValidationConstants.REQUIRED_COLUMNS);
        
        boolean formatValid = true;
        
        // Check for required columns (Action and Unique Key only)
        for (String requiredColumn : ValidationConstants.REQUIRED_COLUMNS) {
            boolean found = false;
            for (String header : headers) {
                log.debug("Comparing required '{}' with header '{}'", requiredColumn.trim(), header.trim());
                if (header.trim().equalsIgnoreCase(requiredColumn.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addError(0, "HEADER", "Missing required column: " + requiredColumn);
                log.warn("Missing required column: {} (available: {})", requiredColumn, headers);
                formatValid = false;
            }
        }
        
        // Warn about unexpected columns but don't fail format validation
        for (String header : headers) {
            if (!ValidationConstants.EXPECTED_COLUMNS.contains(header.trim())) {
                result.addWarning("Unexpected column: " + header);
            }
        }
        
        return formatValid;
    }

    /**
     * Business validation - check data rules
     */
    private void validateBusinessRules(MultipartFile file, ValidationResult result) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            
            List<String> headers = CsvParser.parseCsvLine(headerLine);
            headers = headers.stream()
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toList());
            
            // Track unique codes for duplicate validation
            Set<String> uniqueCodes = new HashSet<>();
            
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                List<String> row = CsvParser.parseCsvLine(line);
                // Ensure all row data is properly trimmed
                row = row.stream()
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toList());
                validateRow(row, headers, rowNumber, result, uniqueCodes);
            }
            
            result.setTotalRows(rowNumber - 1); // Subtract header row
            
        } catch (IOException e) {
            log.error("Error reading CSV file for business validation", e);
            result.addError(0, "FILE", "Error reading file: " + e.getMessage());
        }
    }

    private void validateHeaders(List<String> headers, ValidationResult result) {
        log.info("Parsed headers: {}", headers);
        log.info("Required columns: {}", ValidationConstants.REQUIRED_COLUMNS);
        
        for (String requiredColumn : ValidationConstants.REQUIRED_COLUMNS) {
            boolean found = false;
            for (String header : headers) {
                log.debug("Comparing required '{}' with header '{}'", requiredColumn.trim(), header.trim());
                if (header.trim().equalsIgnoreCase(requiredColumn.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addError(0, "HEADER", "Missing required column: " + requiredColumn);
                log.warn("Missing required column: {} (available: {})", requiredColumn, headers);
            }
        }
        for (String header : headers) {
            if (!ValidationConstants.EXPECTED_COLUMNS.contains(header.trim())) {
                result.addWarning("Unexpected column: " + header);
            }
        }
    }

    private void validateRow(List<String> row, List<String> headers, int rowNumber, ValidationResult result, Set<String> uniqueCodes) {
        boolean rowIsValid = true;
        
        if (row.size() != headers.size()) {
            result.addError(rowNumber, "ROW", "Column count mismatch. Expected " + headers.size() + ", got " + row.size());
            result.setInvalidRows(result.getInvalidRows() + 1);
            return;
        }
        Map<String, String> rowMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            rowMap.put(headers.get(i), row.get(i));
        }
        
        // Create case-insensitive lookup map for validation
        Map<String, String> caseInsensitiveRowMap = new HashMap<>();
        for (Map.Entry<String, String> entry : rowMap.entrySet()) {
            caseInsensitiveRowMap.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        
        log.debug("Row {} data: {}", rowNumber, rowMap);
        
        for (String requiredColumn : ValidationConstants.REQUIRED_COLUMNS) {
            String value = caseInsensitiveRowMap.get(requiredColumn.toLowerCase());
            log.debug("Row {} - Column '{}': value='{}'", rowNumber, requiredColumn, value);
            if (value == null || value.trim().isEmpty()) {
                result.addError(rowNumber, requiredColumn, "Missing required value");
                rowIsValid = false;
            }
        }
        String action = caseInsensitiveRowMap.get("action");
        if (action != null && !action.trim().isEmpty()) {
            if (!ValidationConstants.VALID_ACTIONS.contains(action.trim())) {
                result.addError(rowNumber, "Action", "Invalid action value '" + action + "'. Must be one of: " + ValidationConstants.VALID_ACTIONS);
                rowIsValid = false;
            }
        }
        
        String uniqueKey = caseInsensitiveRowMap.get("unique key");
        if (uniqueKey != null && !uniqueKey.trim().isEmpty()) {
            String trimmedKey = uniqueKey.trim();
            
            // Check for duplicate unique codes
            if (uniqueCodes.contains(trimmedKey)) {
                result.addError(rowNumber, "Unique Key", "Duplicate unique code '" + trimmedKey + "'. Each unique code must appear only once in the file.");
                rowIsValid = false;
            } else {
                // Add to set for future duplicate checking
                uniqueCodes.add(trimmedKey);
            }
            
            // Validate format
            if (!trimmedKey.matches("^[A-Za-z0-9]+$")) {
                result.addError(rowNumber, "Unique Key", "Invalid format '" + trimmedKey + "'. Must be alphanumeric.");
                rowIsValid = false;
            }
        }
        
        // Validate proof requirement for REJECT actions
        // Check if proof file exists for REJECT actions (not from CSV column)
        if ("REJECT".equalsIgnoreCase(action)) {
            String disputeKey = caseInsensitiveRowMap.get("unique key");
            if (disputeKey != null && !disputeKey.trim().isEmpty()) {
                // Check if proof file exists for this unique key
                if (!proofService.proofExists(disputeKey.trim())) {
                    result.addError(rowNumber, "Proof", "Proof file is mandatory for REJECT actions. Please upload proof file for: " + disputeKey.trim());
                    rowIsValid = false;
                }
            }
        }
        
        if (rowIsValid) {
            result.setValidRows(result.getValidRows() + 1);
        } else {
            result.setInvalidRows(result.getInvalidRows() + 1);
        }
    }

}
