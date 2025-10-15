package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.config.ValidationConstants;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CombinedValidationServiceImpl implements CombinedValidationService {

    @Autowired
    private CsvValidationService csvValidationService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private ProofService proofService;
    
    @Autowired
    private BulkDisputeSessionRepository sessionRepository;

    @Override
    public CombinedValidationResult validateCsvWithProofs(MultipartFile csvFile, List<MultipartFile> proofFiles) {
        long startTime = System.currentTimeMillis();
        CombinedValidationResult result = new CombinedValidationResult();
        
        try {
            // Create a map of proof files by unique code (filename without extension)
            Map<String, MultipartFile> proofFileMap = createProofFileMap(proofFiles);
            
            // Parse CSV and validate
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream(), "UTF-8"))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    result.addError(0, "FILE", "File is empty");
                    return result.setElapsedMs(System.currentTimeMillis() - startTime);
                }
                
                List<String> headers = CsvParser.parseCsvLine(headerLine);
                headers = headers.stream()
                        .map(String::trim)
                        .collect(Collectors.toList());
                result.setHeaders(headers);
                
                // Validate headers
                validateHeaders(headers, result);
                
                String line;
                int rowNumber = 1;
                int acceptedCount = 0;
                int rejectedCount = 0;
                int acceptedValid = 0;
                int rejectedValid = 0;
                int missingReceiptCount = 0;
                
                while ((line = reader.readLine()) != null) {
                    rowNumber++;
                    List<String> row = CsvParser.parseCsvLine(line);
                    row = row.stream()
                            .map(String::trim)
                            .collect(Collectors.toList());
                    
                    boolean rowValid = validateRowWithProofs(row, headers, rowNumber, result, proofFileMap);
                    
                    if (rowValid) {
                        result.setValidRows(result.getValidRows() + 1);
                        
                        // Determine action and count accordingly
                        String action = getCaseInsensitiveValue(row, headers, "action");
                        if ("ACCEPT".equalsIgnoreCase(action)) {
                            acceptedCount++;
                            acceptedValid++;
                        } else if ("REJECT".equalsIgnoreCase(action)) {
                            rejectedCount++;
                            rejectedValid++;
                        }
                    } else {
                        result.setInvalidRows(result.getInvalidRows() + 1);
                        
                        // Count missing receipts for rejected rows
                        String action = getCaseInsensitiveValue(row, headers, "action");
                        if ("REJECT".equalsIgnoreCase(action)) {
                            String uniqueKey = getCaseInsensitiveValue(row, headers, "unique key");
                            if (uniqueKey != null && !proofFileMap.containsKey(uniqueKey.trim())) {
                                missingReceiptCount++;
                            }
                        }
                    }
                }
                
                result.setTotalRows(rowNumber - 1); // Subtract header row
                result.setAcceptedSlated(acceptedCount);
                result.setAcceptedSucceeded(acceptedValid);
                result.setAcceptedFailed(acceptedCount - acceptedValid);
                result.setRejectedSlated(rejectedCount);
                result.setRejectedSucceeded(rejectedValid);
                result.setRejectedFailed(rejectedCount - rejectedValid);
                result.setMissingReceipt(missingReceiptCount);
            }
            
        } catch (IOException e) {
            log.error("Error reading CSV file", e);
            result.addError(0, "FILE", "Error reading file: " + e.getMessage());
        }
        
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }
    
    private Map<String, MultipartFile> createProofFileMap(List<MultipartFile> proofFiles) {
        Map<String, MultipartFile> proofFileMap = new HashMap<>();
        
        for (MultipartFile proofFile : proofFiles) {
            if (proofFile != null && !proofFile.isEmpty()) {
                String originalFilename = proofFile.getOriginalFilename();
                if (originalFilename != null && !originalFilename.trim().isEmpty()) {
                    // Extract unique code from filename (remove extension)
                    String uniqueCode = extractUniqueCodeFromFilename(originalFilename);
                    if (uniqueCode != null && !uniqueCode.trim().isEmpty()) {
                        proofFileMap.put(uniqueCode.trim(), proofFile);
                    }
                }
            }
        }
        
        return proofFileMap;
    }
    
    private String extractUniqueCodeFromFilename(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return filename; // No extension, assume entire filename is unique code
        }
        return filename.substring(0, lastDotIndex);
    }
    
    private void validateHeaders(List<String> headers, CombinedValidationResult result) {
        Set<String> requiredHeaders = Set.of("unique key", "action");
        Set<String> headerSet = headers.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        
        for (String requiredHeader : requiredHeaders) {
            if (!headerSet.contains(requiredHeader)) {
                result.addError(0, "HEADER", "Missing required column: " + requiredHeader);
            }
        }
    }
    
    private boolean validateRowWithProofs(List<String> row, List<String> headers, int rowNumber, 
                                         CombinedValidationResult result, Map<String, MultipartFile> proofFileMap) {
        boolean rowIsValid = true;
        
        // Create case-insensitive row map
        Map<String, String> caseInsensitiveRowMap = new HashMap<>();
        for (int i = 0; i < headers.size() && i < row.size(); i++) {
            caseInsensitiveRowMap.put(headers.get(i).toLowerCase(), row.get(i));
        }
        
        // Validate required fields
        String uniqueKey = caseInsensitiveRowMap.get("unique key");
        if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
            result.addError(rowNumber, "Unique Key", "Missing required value");
            rowIsValid = false;
        } else if (!uniqueKey.trim().matches("^[A-Za-z0-9]+$")) {
            result.addError(rowNumber, "Unique Key", "Invalid format '" + uniqueKey + "'. Must be alphanumeric.");
            rowIsValid = false;
        }
        
        String action = caseInsensitiveRowMap.get("action");
        if (action == null || action.trim().isEmpty()) {
            result.addError(rowNumber, "Action", "Missing required value");
            rowIsValid = false;
        } else if (!ValidationConstants.VALID_ACTIONS.contains(action.toUpperCase())) {
            result.addError(rowNumber, "Action", "Invalid action value '" + action + "'. Must be one of: " + ValidationConstants.VALID_ACTIONS);
            rowIsValid = false;
        }
        
        // Validate proof requirement for REJECT actions using uploaded files
        if ("REJECT".equalsIgnoreCase(action) && uniqueKey != null && !uniqueKey.trim().isEmpty()) {
            if (!proofFileMap.containsKey(uniqueKey.trim())) {
                result.addError(rowNumber, "Proof", "Proof file is mandatory for REJECT actions. Please provide proof file for: " + uniqueKey.trim());
                rowIsValid = false;
            }
        }
        
        return rowIsValid;
    }
    
    @Override
    public CombinedValidationResult validateAndSaveCsvWithProofs(MultipartFile csvFile, List<MultipartFile> proofFiles, 
                                                               String uploadedBy, String institutionCode, String merchantId) {
        long startTime = System.currentTimeMillis();
        CombinedValidationResult result = new CombinedValidationResult();
        
        try {
            // First, perform validation
            result = validateCsvWithProofs(csvFile, proofFiles);
            
            // If validation fails, return early without saving files
            if (!result.isValid()) {
                result.setElapsedMs(System.currentTimeMillis() - startTime);
                return result;
            }
            
            // Create session with temporary file path first
            BulkDisputeSession session = BulkDisputeSession.builder()
                    .institutionCode(institutionCode)
                    .merchantId(merchantId)
                    .uploadedBy(uploadedBy)
                    .filePath("temp_" + System.currentTimeMillis() + ".csv") // Temporary path
                    .fileName(csvFile.getOriginalFilename())
                    .fileSize(csvFile.getSize())
                    .status(BulkDisputeSession.SessionStatus.VALIDATED)
                    .totalRows(result.getTotalRows())
                    .validRows(result.getValidRows())
                    .invalidRows(result.getInvalidRows())
                    .version(0)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            
            session = sessionRepository.save(session);
            result.setSessionId(session.getId());
            
            // Save CSV file and update session with actual file path
            String csvFilePath = fileService.saveSessionFile(session.getId(), csvFile);
            session.setFilePath(csvFilePath);
            sessionRepository.save(session);
            result.setCsvFilePath(csvFilePath);
            
            // Save proof files to proofs folder
            List<String> savedProofPaths = new java.util.ArrayList<>();
            for (MultipartFile proofFile : proofFiles) {
                if (proofFile != null && !proofFile.isEmpty()) {
                    String originalFilename = proofFile.getOriginalFilename();
                    if (originalFilename != null && !originalFilename.trim().isEmpty()) {
                        String uniqueCode = extractUniqueCodeFromFilename(originalFilename);
                        if (uniqueCode != null && !uniqueCode.trim().isEmpty()) {
                            try {
                                String proofFilePath = proofService.uploadProof(uniqueCode.trim(), proofFile);
                                savedProofPaths.add(proofFilePath);
                            } catch (Exception e) {
                                log.warn("Failed to save proof file for unique code {}: {}", uniqueCode, e.getMessage());
                                // Continue with other files even if one fails
                            }
                        }
                    }
                }
            }
            result.setProofFilePaths(savedProofPaths);
            
        } catch (Exception e) {
            log.error("Error during validation and file saving", e);
            result.addError(0, "FILE", "Error saving files: " + e.getMessage());
        }
        
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }
    
    private String getCaseInsensitiveValue(List<String> row, List<String> headers, String columnName) {
        for (int i = 0; i < headers.size() && i < row.size(); i++) {
            if (headers.get(i).toLowerCase().equals(columnName.toLowerCase())) {
                return row.get(i);
            }
        }
        return null;
    }
    
    @Override
    public Map<String, Object> validateSessionAndProofs(MultipartFile csvFile, List<MultipartFile> proofFiles, 
                                                       String uploadedBy, String institutionCode, String merchantId) {
        try {
            // Validate CSV file is provided
            if (csvFile == null || csvFile.isEmpty()) {
                return Map.of("error", "CSV file is required");
            }
            
            // Handle null receipt files
            if (proofFiles == null) {
                proofFiles = new java.util.ArrayList<>();
            }
            
            // Filter out null/empty files
            proofFiles = proofFiles.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            
            // Perform combined validation and save files
            CombinedValidationResult result = validateAndSaveCsvWithProofs(csvFile, proofFiles, uploadedBy, institutionCode, merchantId);
            
            if (!result.isValid()) {
                return Map.of(
                        "error", "Validation failed",
                        "validationErrors", result.getErrors()
                );
            }
            
            // Return success response with the specified structure and session ID
            Map<String, Object> response = new java.util.HashMap<>(result.toResponseMap());
            if (result.getSessionId() != null) {
                response.put("sessionId", result.getSessionId());
            }
            return response;
            
        } catch (Exception e) {
            log.error("Error validating session and proof files", e);
            return Map.of("error", "Validation failed: " + e.getMessage());
        }
    }
}
