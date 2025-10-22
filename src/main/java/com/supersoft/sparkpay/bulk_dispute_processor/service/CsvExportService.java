package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionErrorRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CsvExportService {

    @Autowired
    private BulkDisputeSessionErrorRepository errorRepository;

    /**
     * Export CSV with errors column
     */
    public byte[] exportCsvWithErrors(Long sessionId, String filePath) {
        try {
            log.info("Exporting CSV with errors for session {} from file {}", sessionId, filePath);
            
            // Read original CSV
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            if (lines.isEmpty()) {
                throw new IOException("File is empty");
            }
            
            // Get all errors for the session
            List<BulkDisputeSessionErrorRepository.SessionError> allErrors = errorRepository.getErrorsBySessionId(sessionId);
            Map<Integer, List<BulkDisputeSessionErrorRepository.SessionError>> errorsByRow = allErrors.stream()
                    .collect(Collectors.groupingBy(BulkDisputeSessionErrorRepository.SessionError::getRowNumber));
            
            // Create new CSV with errors column
            List<String> newLines = new ArrayList<>();
            
            // Process header row
            String headerLine = lines.get(0);
            List<String> headers = CsvParser.parseCsvLine(headerLine);
            headers.add("Errors"); // Add Errors column
            newLines.add(String.join(",", headers));
            
            // Process data rows
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                List<String> row = CsvParser.parseCsvLine(line);
                
                // Get errors for this row
                List<BulkDisputeSessionErrorRepository.SessionError> rowErrors = errorsByRow.get(i);
                String errorMessages = "";
                
                if (rowErrors != null && !rowErrors.isEmpty()) {
                    errorMessages = rowErrors.stream()
                            .map(BulkDisputeSessionErrorRepository.SessionError::getErrorMessage)
                            .collect(Collectors.joining(" | "));
                } else {
                    errorMessages = "No errors";
                }
                
                // Add error messages to row
                row.add(errorMessages);
                newLines.add(String.join(",", row));
            }
            
            // Convert to byte array
            String csvContent = String.join("\n", newLines);
            return csvContent.getBytes("UTF-8");
            
        } catch (Exception e) {
            log.error("Error exporting CSV with errors for session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Failed to export CSV with errors: " + e.getMessage());
        }
    }

    /**
     * Export original CSV without errors
     */
    public byte[] exportOriginalCsv(String filePath) {
        try {
            log.info("Exporting original CSV from file {}", filePath);
            return Files.readAllBytes(Paths.get(filePath));
        } catch (Exception e) {
            log.error("Error exporting original CSV from file {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Failed to export original CSV: " + e.getMessage());
        }
    }
}
