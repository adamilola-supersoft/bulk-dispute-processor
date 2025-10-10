package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.config.ValidationConstants;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Slf4j
@Service
public class CsvValidationServiceImpl implements CsvValidationService {

    public ValidationResult validateCsv(MultipartFile file) {
        ValidationResult result = new ValidationResult();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.addError("File is empty");
                return result;
            }
            
            List<String> headers = CsvParser.parseCsvLine(headerLine);
            result.setHeaders(headers);
            
            validateHeaders(headers, result);
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                List<String> row = CsvParser.parseCsvLine(line);
                validateRow(row, headers, rowNumber, result);
            }
            
            result.setTotalRows(rowNumber - 1); // Subtract header row
            
        } catch (IOException e) {
            log.error("Error reading CSV file", e);
            result.addError("Error reading file: " + e.getMessage());
        }
        
        return result;
    }

    private void validateHeaders(List<String> headers, ValidationResult result) {
        log.info("Parsed headers: {}", headers);
        log.info("Required columns: {}", ValidationConstants.REQUIRED_COLUMNS);
        
        for (String requiredColumn : ValidationConstants.REQUIRED_COLUMNS) {
            boolean found = false;
            for (String header : headers) {
                log.debug("Comparing required '{}' with header '{}'", requiredColumn.trim(), header.trim());
                if (header.trim().equals(requiredColumn.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addError("Missing required column: " + requiredColumn);
                log.warn("Missing required column: {} (available: {})", requiredColumn, headers);
            }
        }
        for (String header : headers) {
            if (!ValidationConstants.EXPECTED_COLUMNS.contains(header.trim())) {
                result.addWarning("Unexpected column: " + header);
            }
        }
    }

    private void validateRow(List<String> row, List<String> headers, int rowNumber, ValidationResult result) {
        boolean rowIsValid = true;
        
        if (row.size() != headers.size()) {
            result.addError("Row " + rowNumber + ": Column count mismatch. Expected " + headers.size() + ", got " + row.size());
            result.setInvalidRows(result.getInvalidRows() + 1);
            return;
        }
        Map<String, String> rowMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            rowMap.put(headers.get(i), row.get(i));
        }
        
        log.debug("Row {} data: {}", rowNumber, rowMap);
        
        for (String requiredColumn : ValidationConstants.REQUIRED_COLUMNS) {
            String value = rowMap.get(requiredColumn);
            log.debug("Row {} - Column '{}': value='{}'", rowNumber, requiredColumn, value);
            if (value == null || value.trim().isEmpty()) {
                result.addError("Row " + rowNumber + ": Missing required value for column '" + requiredColumn + "'");
                rowIsValid = false;
            }
        }
        String action = rowMap.get("Action");
        if (action != null && !action.trim().isEmpty()) {
            if (!ValidationConstants.VALID_ACTIONS.contains(action.trim())) {
                result.addError("Row " + rowNumber + ": Invalid action value '" + action + "'. Must be one of: " + ValidationConstants.VALID_ACTIONS);
                rowIsValid = false;
            }
        }
        
        String uniqueKey = rowMap.get("Unique Key");
        if (uniqueKey != null && !uniqueKey.trim().isEmpty()) {
            if (!uniqueKey.trim().matches("^[A-Za-z0-9]+$")) {
                result.addError("Row " + rowNumber + ": Invalid Unique Key format '" + uniqueKey + "'. Must be alphanumeric.");
                rowIsValid = false;
            }
        }
        if (rowIsValid) {
            result.setValidRows(result.getValidRows() + 1);
        } else {
            result.setInvalidRows(result.getInvalidRows() + 1);
        }
    }

}
