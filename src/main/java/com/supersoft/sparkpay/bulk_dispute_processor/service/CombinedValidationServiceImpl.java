package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.config.ValidationConstants;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${bulk.proofs.max-size-mb:10}")
    private int maxSizeMb;
    
    @Value("${bulk.proofs.allowed-extensions:pdf,jpg,jpeg,png,doc,docx}")
    private String allowedExtensions;
    
    @Value("${bulk.proofs.replace-existing:true}")
    private boolean replaceExisting;

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
                
                // Track unique codes for duplicate validation
                Set<String> uniqueCodes = new HashSet<>();
                
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
                    
                    boolean rowValid = validateRowWithProofs(row, headers, rowNumber, result, proofFileMap, uniqueCodes);
                    
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
    
    private void validateProofFile(MultipartFile proofFile, String uniqueCode, int rowNumber, CombinedValidationResult result) {
        try {
            // Validate file size
            if (proofFile.getSize() > (long) maxSizeMb * 1024 * 1024) {
                result.addError(rowNumber, "Proof", "Proof file too large for " + uniqueCode + ". Maximum size: " + maxSizeMb + "MB");
                return;
            }

            // Validate file extension
            String originalFilename = proofFile.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                result.addError(rowNumber, "Proof", "Proof file must have a valid filename for " + uniqueCode);
                return;
            }

            String fileExtension = getFileExtension(originalFilename);
            List<String> allowedExts = Arrays.asList(allowedExtensions.split(","));
            if (!allowedExts.contains(fileExtension.toLowerCase())) {
                result.addError(rowNumber, "Proof", "Invalid file type for " + uniqueCode + ". Allowed extensions: " + allowedExtensions);
            }
        } catch (Exception e) {
            log.error("Error validating proof file for unique code {}: {}", uniqueCode, e.getMessage(), e);
            result.addError(rowNumber, "Proof", "Error validating proof file for " + uniqueCode + ": " + e.getMessage());
        }
    }
    
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
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
                                         CombinedValidationResult result, Map<String, MultipartFile> proofFileMap, Set<String> uniqueCodes) {
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
        } else {
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
            } else {
                // Validate the proof file if it exists
                MultipartFile proofFile = proofFileMap.get(uniqueKey.trim());
                validateProofFile(proofFile, uniqueKey.trim(), rowNumber, result);
                if (result.getErrors().stream().anyMatch(error -> error.getRow() == rowNumber && "Proof".equals(error.getColumn()))) {
                    rowIsValid = false;
                }
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
            log.info("Starting CSV validation for file: {}", csvFile.getOriginalFilename());
            
            // First, perform validation
            result = validateCsvWithProofs(csvFile, proofFiles);
            
            // If validation fails, return early without saving files
            if (!result.isValid()) {
                log.warn("CSV validation failed with {} errors", result.getErrors().size());
                result.setElapsedMs(System.currentTimeMillis() - startTime);
                return result;
            }
            
            log.info("CSV validation successful, creating session");
            
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
            
            try {
                session = sessionRepository.save(session);
                log.info("Session created with ID: {}", session.getId());
                result.setSessionId(session.getId());
            } catch (Exception e) {
                log.error("Failed to create session in database", e);
                result.addError(0, "DATABASE", "Failed to create session: " + e.getMessage());
                result.setElapsedMs(System.currentTimeMillis() - startTime);
                return result;
            }
            
            // Save CSV file and update session with actual file path
            try {
                String csvFilePath = fileService.saveSessionFile(session.getId(), csvFile);
                session.setFilePath(csvFilePath);
                sessionRepository.save(session);
                result.setCsvFilePath(csvFilePath);
                log.info("CSV file saved to: {}", csvFilePath);
            } catch (Exception e) {
                log.error("Failed to save CSV file", e);
                result.addError(0, "FILE", "Failed to save CSV file: " + e.getMessage());
                result.setElapsedMs(System.currentTimeMillis() - startTime);
                return result;
            }
            
            // Save proof files to proofs folder (without validation since we already validated)
            List<String> savedProofPaths = new java.util.ArrayList<>();
            List<String> failedProofs = new java.util.ArrayList<>();
            
            for (MultipartFile proofFile : proofFiles) {
                if (proofFile != null && !proofFile.isEmpty()) {
                    String originalFilename = proofFile.getOriginalFilename();
                    if (originalFilename != null && !originalFilename.trim().isEmpty()) {
                        String uniqueCode = extractUniqueCodeFromFilename(originalFilename);
                        if (uniqueCode != null && !uniqueCode.trim().isEmpty()) {
                            try {
                                String proofFilePath = proofService.uploadProofWithoutValidation(uniqueCode.trim(), proofFile, replaceExisting);
                                savedProofPaths.add(proofFilePath);
                                log.info("Proof file saved for unique code: {}", uniqueCode);
                            } catch (Exception e) {
                                log.warn("Failed to save proof file for unique code {}: {}", uniqueCode, e.getMessage());
                                failedProofs.add(uniqueCode + ": " + e.getMessage());
                                // Continue with other files even if one fails
                            }
                        }
                    }
                }
            }
            
            result.setProofFilePaths(savedProofPaths);
            
            // If some proof files failed, add a warning but don't fail the entire operation
            if (!failedProofs.isEmpty()) {
                log.warn("Some proof files failed to save: {}", failedProofs);
                result.addError(0, "PROOF_FILES", "Some proof files failed to save: " + String.join(", ", failedProofs));
            }
            
            log.info("Validation and file saving completed successfully. Saved {} proof files", savedProofPaths.size());
            
        } catch (Exception e) {
            log.error("Unexpected error during validation and file saving", e);
            result.addError(0, "SYSTEM", "Unexpected error: " + e.getMessage());
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
            log.info("Starting validation for CSV: {} and {} proof files (replaceExisting: {})", 
                    csvFile != null ? csvFile.getOriginalFilename() : "null", 
                    proofFiles != null ? proofFiles.size() : 0,
                    replaceExisting);
            
            // Validate CSV file is provided
            if (csvFile == null || csvFile.isEmpty()) {
                log.warn("CSV file is null or empty");
                return Map.of("error", "CSV file is required");
            }
            
            // Handle null receipt files
            if (proofFiles == null) {
                log.info("Proof files list is null, initializing empty list");
                proofFiles = new java.util.ArrayList<>();
            }
            
            // Filter out null/empty files
            List<MultipartFile> validProofFiles = proofFiles.stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("Filtered to {} valid proof files out of {} total", validProofFiles.size(), proofFiles.size());
            
            // Perform combined validation and save files
            CombinedValidationResult result = validateAndSaveCsvWithProofs(csvFile, validProofFiles, uploadedBy, institutionCode, merchantId);
            
            if (!result.isValid()) {
                log.warn("Validation failed with {} errors", result.getErrors().size());
                return Map.of(
                        "error", "Validation failed",
                        "validationErrors", result.getErrors()
                );
            }
            
            log.info("Validation successful, session ID: {}", result.getSessionId());
            
            // Return success response with the specified structure and session ID
            Map<String, Object> response = new java.util.HashMap<>(result.toResponseMap());
            if (result.getSessionId() != null) {
                response.put("sessionId", result.getSessionId());
            }
            return response;
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return Map.of("error", "Validation error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error validating session and proof files", e);
            return Map.of("error", "Validation failed: " + e.getMessage());
        }
    }
}
