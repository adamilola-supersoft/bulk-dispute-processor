package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionErrorRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.DisputeRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class BulkDisputeSessionServiceImpl implements BulkDisputeSessionService {

    @Autowired
    private BulkDisputeSessionRepository sessionRepository;
    
    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private CsvValidationService validationService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private JobMessagePublisher messagePublisher;
    
    @Autowired
    private LiveStatusService liveStatusService;
    
    @Autowired
    private BulkDisputeSessionErrorRepository errorRepository;

    @Override
    public SessionUploadResult uploadSession(MultipartFile file, String uploadedBy, String institutionCode, String merchantId) {
        try {
            log.info("Uploading session file: {}", file.getOriginalFilename());

            String tempFilePath = "temp_" + System.currentTimeMillis() + ".csv";
            BulkDisputeSession session = BulkDisputeSession.builder()
                    .institutionCode(institutionCode)
                    .merchantId(merchantId)
                    .uploadedBy(uploadedBy)
                    .filePath(tempFilePath)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .status(BulkDisputeSession.SessionStatus.UPLOADED)
                    .totalRows(0)
                    .validRows(0)
                    .invalidRows(0)
                    .version(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            session = sessionRepository.save(session);

            String finalFilePath = fileService.saveSessionFile(session.getId(), file);
            session.setFilePath(finalFilePath);
            sessionRepository.save(session);

            CsvValidationService.ValidationResult validationResult = validationService.validateCsv(file);
            
            // Check format validation first
            if (!validationResult.isFormatValid()) {
                session.setStatus(BulkDisputeSession.SessionStatus.UPLOADED);
                sessionRepository.save(session);
                
                return SessionUploadResult.validationFailure(validationResult.getErrors());
            }

            // Store validation errors if any
            if (!validationResult.getErrors().isEmpty()) {
                storeValidationErrors(session.getId(), validationResult);
            }

            session.setStatus(BulkDisputeSession.SessionStatus.VALIDATED);
            session.setTotalRows(validationResult.getTotalRows());
            session.setValidRows(validationResult.getValidRows());
            session.setInvalidRows(validationResult.getInvalidRows());
            sessionRepository.save(session);

            List<Map<String, String>> preview = getPreviewData(finalFilePath, 200);

            return SessionUploadResult.success(session.getId(), preview, session.getTotalRows(), session.getVersion());

        } catch (Exception e) {
            log.error("Error uploading session", e);
            return SessionUploadResult.failure("Upload failed: " + e.getMessage());
        }
    }

    @Override
    public SessionPreviewResult getSessionPreview(Long sessionId, int rows, int offset) {
        return getSessionPreview(sessionId, rows, offset, false);
    }

    @Override
    public SessionPreviewResult getSessionPreview(Long sessionId, int rows, int offset, boolean includeErrors) {
        try {
            Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return SessionPreviewResult.failure("Session not found");
            }

            BulkDisputeSession session = sessionOpt.get();
            List<Map<String, String>> preview = getPreviewData(session.getFilePath(), rows);

            // Add error information if requested
            if (includeErrors) {
                addErrorInformationToPreview(sessionId, preview, offset, rows);
            }

            // Calculate error information
            boolean hasErrors = errorRepository.hasAnyErrors(sessionId);
            List<Integer> pagesWithErrors = calculatePagesWithErrors(sessionId, session.getTotalRows(), rows);

            return SessionPreviewResult.success(sessionId, preview, session.getTotalRows(), session.getVersion(), hasErrors, pagesWithErrors);

        } catch (Exception e) {
            log.error("Error getting preview for session {}", sessionId, e);
            return SessionPreviewResult.failure("Preview failed: " + e.getMessage());
        }
    }

    @Override
    public SessionOverwriteResult overwriteSessionFile(Long sessionId, MultipartFile file, String ifMatch) {
        try {
            Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return SessionOverwriteResult.failure("Session not found");
            }

            BulkDisputeSession session = sessionOpt.get();

            if (ifMatch != null && !ifMatch.equals(String.valueOf(session.getVersion()))) {
                return SessionOverwriteResult.failure("Version mismatch. Expected: " + session.getVersion() + ", Got: " + ifMatch);
            }

            CsvValidationService.ValidationResult validationResult = validationService.validateCsv(file);
            if (!validationResult.isValid()) {
                return SessionOverwriteResult.failure("Validation failed: " + validationResult.getErrorSummary());
            }

            String filePath = fileService.overwriteSessionFile(sessionId, file);
            session.setFilePath(filePath);
            session.setTotalRows(validationResult.getTotalRows());
            session.setValidRows(validationResult.getTotalRows());
            session.setInvalidRows(0);
            session.setStatus(BulkDisputeSession.SessionStatus.PREVIEWED);
            session = sessionRepository.save(session);

            return SessionOverwriteResult.success(sessionId, session.getVersion(), session.getTotalRows());

        } catch (Exception e) {
            log.error("Error overwriting file for session {}", sessionId, e);
            return SessionOverwriteResult.failure("Overwrite failed: " + e.getMessage());
        }
    }

    @Override
    public SessionConfirmResult confirmSession(Long sessionId) {
        try {
            Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return SessionConfirmResult.failure("Session not found");
            }

            BulkDisputeSession session = sessionOpt.get();
            if (session.getStatus() != BulkDisputeSession.SessionStatus.VALIDATED) {
                return SessionConfirmResult.failure("Session must be VALIDATED to confirm");
            }

            BulkDisputeJob job = BulkDisputeJob.builder()
                    .sessionId(sessionId)
                    .jobRef("JOB-" + System.currentTimeMillis())
                    .status(BulkDisputeJob.JobStatus.PENDING)
                    .totalRows(session.getTotalRows())
                    .processedRows(0)
                    .successCount(0)
                    .failureCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            job = jobRepository.save(job);

            session.setStatus(BulkDisputeSession.SessionStatus.CONFIRMED);
            sessionRepository.save(session);

            JobMessagePublisher.JobMessage jobMessage = new JobMessagePublisher.JobMessage(
                    job.getId(), sessionId, session.getFilePath(), session.getUploadedBy());
            messagePublisher.publishJobMessage(jobMessage);

            return SessionConfirmResult.success(job.getId());

        } catch (Exception e) {
            log.error("Error confirming session {}", sessionId, e);
            return SessionConfirmResult.failure("Confirmation failed: " + e.getMessage());
        }
    }

    @Override
    public byte[] getSessionFileBlob(Long sessionId) throws IOException {
        Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new IOException("Session not found");
        }
        
        BulkDisputeSession session = sessionOpt.get();
        return Files.readAllBytes(Paths.get(session.getFilePath()));
    }

    @Override
    public String getSessionFilePath(Long sessionId) {
        Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return null;
        }
        
        return sessionOpt.get().getFilePath();
    }

    @Override
    public SessionListResult getSessions(int page, int size, String status, String uploadedBy, String institutionCode, String merchantId) {
        try {
            List<SessionSummary> sessions = sessionRepository.findSessionsWithPagination(page, size, status, uploadedBy, institutionCode, merchantId);
            long totalElements = sessionRepository.countSessions(status, uploadedBy, institutionCode, merchantId);
            int totalPages = (int) Math.ceil((double) totalElements / size);
            
            return SessionListResult.success(sessions, totalPages, totalElements, page, size);
        } catch (Exception e) {
            log.error("Error getting sessions", e);
            return SessionListResult.failure("Failed to retrieve sessions: " + e.getMessage());
        }
    }

    private List<Map<String, String>> getPreviewData(String filePath, int maxRows) throws IOException {
        List<Map<String, String>> preview = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        List<String> uniqueKeys = new ArrayList<>();
        
        try (var reader = Files.newBufferedReader(Paths.get(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine != null) {
                headers = parseCsvLine(headerLine);
                // Clean headers by removing BOM from each header
                headers = headers.stream()
                        .map(header -> CsvParser.hasBom(header) ? header.substring(1) : header)
                        .collect(java.util.stream.Collectors.toList());
            }
            
            String line;
            int rowCount = 0;
            while ((line = reader.readLine()) != null && rowCount < maxRows) {
                List<String> row = parseCsvLine(line);
                Map<String, String> rowMap = new HashMap<>();
                for (int i = 0; i < headers.size() && i < row.size(); i++) {
                    rowMap.put(headers.get(i), row.get(i));
                }
                preview.add(rowMap);
                
                // Collect unique keys for live status lookup
                String uniqueKey = rowMap.get("Unique Key");
                if (uniqueKey != null && !uniqueKey.trim().isEmpty()) {
                    uniqueKeys.add(uniqueKey.trim());
                }
                
                rowCount++;
            }
        }
        
        // Get live statuses for all unique keys
        if (!uniqueKeys.isEmpty()) {
            log.info("Getting live statuses for {} unique keys: {}", uniqueKeys.size(), uniqueKeys);
            try {
                Map<String, DisputeRepository.DisputeStatusInfo> liveStatuses = liveStatusService.getLiveStatuses(uniqueKeys);
                log.info("Retrieved {} live statuses from database", liveStatuses.size());
                
                // Add live status to each row
                for (Map<String, String> row : preview) {
                    String uniqueKey = row.get("Unique Key");
                    if (uniqueKey != null && !uniqueKey.trim().isEmpty()) {
                        DisputeRepository.DisputeStatusInfo statusInfo = liveStatuses.get(uniqueKey.trim());
                        if (statusInfo != null) {
                            row.put("Live Status", statusInfo.getStatusDescription());
                            if (statusInfo.getResolvedBy() != null) {
                                row.put("Resolved By", statusInfo.getResolvedBy());
                            }
                            if (statusInfo.getDateModified() != null) {
                                row.put("Date Modified", statusInfo.getDateModified().toString());
                            }
                            log.debug("Added live status for {}: {}", uniqueKey, statusInfo.getStatusDescription());
                        } else {
                            row.put("Live Status", "NotFound");
                            log.debug("No live status found for {}", uniqueKey);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to get live statuses for preview", e);
                // Continue without live status if there's an error
            }
        } else {
            log.info("No unique keys found for live status lookup");
        }
        
        return preview;
    }

    /**
     * Store validation errors in the database
     */
    private void storeValidationErrors(Long sessionId, CsvValidationService.ValidationResult validationResult) {
        try {
            List<BulkDisputeSessionErrorRepository.SessionError> errors = validationResult.getErrors().stream()
                    .map(error -> new BulkDisputeSessionErrorRepository.SessionError(
                            error.getRow(),
                            error.getColumn(),
                            error.getReason()
                    ))
                    .collect(java.util.stream.Collectors.toList());
            
            errorRepository.saveErrors(sessionId, errors);
            log.info("Stored {} validation errors for session {}", errors.size(), sessionId);
        } catch (Exception e) {
            log.error("Error storing validation errors for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Add error information to preview data
     */
    private void addErrorInformationToPreview(Long sessionId, List<Map<String, String>> preview, int offset, int rows) {
        try {
            // Get row numbers for current page
            List<Integer> rowNumbers = new ArrayList<>();
            for (int i = 0; i < preview.size(); i++) {
                rowNumbers.add(offset + i + 1); // +1 because rows are 1-indexed
            }
            
            // Get errors for these rows
            List<BulkDisputeSessionErrorRepository.SessionError> errors = errorRepository.getErrorsForRows(sessionId, rowNumbers);
            Map<Integer, List<BulkDisputeSessionErrorRepository.SessionError>> errorsByRow = errors.stream()
                    .collect(java.util.stream.Collectors.groupingBy(BulkDisputeSessionErrorRepository.SessionError::getRowNumber));
            
            // Add error information to each row
            for (int i = 0; i < preview.size(); i++) {
                Map<String, String> row = preview.get(i);
                int rowNumber = offset + i + 1;
                
                List<BulkDisputeSessionErrorRepository.SessionError> rowErrors = errorsByRow.get(rowNumber);
                if (rowErrors != null && !rowErrors.isEmpty()) {
                    row.put("hasErrors", "true");
                    String errorMessages = rowErrors.stream()
                            .map(BulkDisputeSessionErrorRepository.SessionError::getErrorMessage)
                            .collect(java.util.stream.Collectors.joining(" | "));
                    row.put("errors", errorMessages);
                } else {
                    row.put("hasErrors", "false");
                    row.put("errors", "");
                }
            }
        } catch (Exception e) {
            log.error("Error adding error information to preview for session {}: {}", sessionId, e.getMessage());
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        result.add(currentField.toString().trim());
        return result;
    }

    /**
     * Calculate which pages have errors based on pagination
     */
    private List<Integer> calculatePagesWithErrors(Long sessionId, int totalRows, int pageSize) {
        try {
            // Get all rows with errors
            List<BulkDisputeSessionErrorRepository.SessionError> allErrors = errorRepository.getErrorsBySessionId(sessionId);
            Set<Integer> rowsWithErrors = allErrors.stream()
                    .map(BulkDisputeSessionErrorRepository.SessionError::getRowNumber)
                    .collect(java.util.stream.Collectors.toSet());
            
            if (rowsWithErrors.isEmpty()) {
                return List.of();
            }
            
            // Calculate which pages contain rows with errors
            Set<Integer> pagesWithErrors = new HashSet<>();
            for (int rowNumber : rowsWithErrors) {
                int pageNumber = (rowNumber - 1) / pageSize; // Convert to 0-based page number
                pagesWithErrors.add(pageNumber);
            }
            
            return pagesWithErrors.stream().sorted().collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.error("Error calculating pages with errors for session {}: {}", sessionId, e.getMessage());
            return List.of();
        }
    }
}
