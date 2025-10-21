package com.supersoft.sparkpay.bulk_dispute_processor.controller;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.service.BulkDisputeSessionService;
import com.supersoft.sparkpay.bulk_dispute_processor.service.ProofService;
import com.supersoft.sparkpay.bulk_dispute_processor.service.EnhancedJobProcessor;
import com.supersoft.sparkpay.bulk_dispute_processor.service.JobResumeService;
import com.supersoft.sparkpay.bulk_dispute_processor.service.JobRetryService;
import com.supersoft.sparkpay.bulk_dispute_processor.service.CombinedValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Encoding;
import org.springframework.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "Bulk Dispute Processing", description = "API for processing bulk dispute CSV uploads")
public class BulkDisputeController {

    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private BulkDisputeSessionService sessionService;
    
    @Autowired
    private ProofService proofService;
    
    @Autowired
    private EnhancedJobProcessor enhancedJobProcessor;
    
    @Autowired
    private JobResumeService jobResumeService;
    
    @Autowired
    private JobRetryService jobRetryService;
    
    @Autowired
    private CombinedValidationService combinedValidationService;

    @Operation(summary = "Upload CSV file and create session", 
               description = "Upload a CSV file containing dispute data and create a new processing session")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session created successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "sessionId": 123,
                      "preview": [
                        {
                          "Unique Key": "2214B8JO003524000000003524",
                          "Action": "ACCEPT",
                          "Proof(Optional)": ""
                        },
                        {
                          "Unique Key": "2070EXNV012946000000012946",
                          "Action": "REJECT",
                          "Proof(Optional)": "Document1.pdf"
                        }
                      ],
                      "totalRows": 100,
                      "version": 0
                    }
                    """))),
        @ApiResponse(responseCode = "422", description = "Validation failed",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Validation failed",
                      "validationErrors": [
                        {
                          "row": 0,
                          "column": "HEADER",
                          "reason": "Missing required column: Unique Key"
                        },
                        {
                          "row": 5,
                          "column": "Action",
                          "reason": "Invalid action value 'PENDING'. Must be one of: [ACCEPT, REJECT, Accept, Reject]"
                        },
                        {
                          "row": 3,
                          "column": "Unique Key",
                          "reason": "Missing required value"
                        }
                      ]
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Upload failed: File too large"
                    }
                    """)))
    })
    @PostMapping(value = "/sessions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSession(
            @Parameter(description = "CSV file containing dispute data", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "User who uploaded the file")
            @RequestParam(value = "uploadedBy", defaultValue = "system") String uploadedBy,
            @Parameter(description = "Institution code")
            @RequestParam("institutionCode") String institutionCode,
            @Parameter(description = "Merchant ID")
            @RequestParam("merchantId") String merchantId) {
        
        BulkDisputeSessionService.SessionUploadResult result = sessionService.uploadSession(file, uploadedBy, institutionCode, merchantId);
        
        if (!result.isSuccess()) {
            if (result.getValidationErrors() != null && !result.getValidationErrors().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(Map.of(
                                "error", result.getError(),
                                "validationErrors", result.getValidationErrors()
                        ));
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(Map.of("error", result.getError()));
            }
        }
        
        return ResponseEntity.ok(Map.of(
                "sessionId", result.getSessionId(),
                "preview", result.getPreview(),
                "totalRows", result.getTotalRows(),
                "version", result.getVersion()
        ));
    }

    @Operation(summary = "Preview CSV data", 
               description = "Get a preview of the CSV data in the session with pagination")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Preview data retrieved successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "sessionId": 123,
                      "preview": [
                        {
                          "Unique Key": "2214B8JO003524000000003524",
                          "Action": "ACCEPT",
                          "Proof(Optional)": "",
                          "Live Status": "PENDING",
                          "Processed": "false"
                        },
                        {
                          "Unique Key": "2070EXNV012946000000012946",
                          "Action": "REJECT",
                          "Proof(Optional)": "Document1.pdf",
                          "Live Status": "ACCEPTED",
                          "Processed": "true",
                          "Resolved By": "system",
                          "Date Modified": "2025-10-20 15:30:45"
                        }
                      ],
                      "totalRows": 100,
                      "version": 0
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Session not found"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Preview failed: File not found"
                    }
                    """)))
    })
    @GetMapping("/sessions/preview/{sessionId}")
    public ResponseEntity<?> getPreview(
            @Parameter(description = "Session ID", required = true)
            @PathVariable Long sessionId,
            @Parameter(description = "Number of rows to return")
            @RequestParam(value = "rows", defaultValue = "200") int rows,
            @Parameter(description = "Number of rows to skip")
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        
        BulkDisputeSessionService.SessionPreviewResult result = sessionService.getSessionPreview(sessionId, rows, offset);
        
        if (!result.isSuccess()) {
            if (result.getError().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", result.getError()));
        }
        
        return ResponseEntity.ok(Map.of(
                "sessionId", result.getSessionId(),
                "preview", result.getPreview(),
                "totalRows", result.getTotalRows(),
                "version", result.getVersion()
        ));
    }

    @Operation(summary = "Overwrite session file", 
               description = "Overwrite the CSV file for an existing session with optimistic locking")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File overwritten successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "sessionId": 123,
                      "version": 1,
                      "totalRows": 150
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Session not found"
                    }
                    """))),
        @ApiResponse(responseCode = "409", description = "Version mismatch",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Version mismatch. Expected: 1, Got: 0"
                    }
                    """))),
        @ApiResponse(responseCode = "422", description = "Validation failed",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Validation failed",
                      "details": "Missing required column: Action"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Overwrite failed: File system error"
                    }
                    """)))
    })
    @PutMapping(value = "/sessions/file/{sessionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> overwriteFile(
            @Parameter(description = "Session ID", required = true)
            @PathVariable Long sessionId,
            @Parameter(description = "New CSV file to replace the existing one", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Version for optimistic locking")
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        
        BulkDisputeSessionService.SessionOverwriteResult result = sessionService.overwriteSessionFile(sessionId, file, ifMatch);
        
        if (!result.isSuccess()) {
            if (result.getError().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            if (result.getError().contains("Version mismatch")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", result.getError()));
            }
            if (result.getError().contains("Validation failed")) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(Map.of("error", result.getError()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", result.getError()));
        }
        
        return ResponseEntity.ok(Map.of(
                "sessionId", result.getSessionId(),
                "version", result.getVersion(),
                "totalRows", result.getTotalRows()
        ));
    }

    @Operation(summary = "Confirm session and create job", 
               description = "Confirm the session and create a processing job that will be queued for background processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job created successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "jobId": 456
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Session not found"
                    }
                    """))),
                @ApiResponse(responseCode = "400", description = "Session not in valid state for confirmation",
                            content = @Content(schema = @Schema(example = """
                            {
                              "error": "Session must be VALIDATED to confirm"
                            }
                            """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Confirmation failed: Database connection error"
                    }
                    """)))
    })
    @PostMapping("/sessions/confirm/{sessionId}")
    public ResponseEntity<?> confirmSession(
            @Parameter(description = "Session ID to confirm", required = true)
            @PathVariable Long sessionId) {
        
        BulkDisputeSessionService.SessionConfirmResult result = sessionService.confirmSession(sessionId);
        
        if (!result.isSuccess()) {
            if (result.getError().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
                    if (result.getError().contains("must be VALIDATED")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", result.getError()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", result.getError()));
        }
        
        return ResponseEntity.ok(Map.of("jobId", result.getJobId()));
    }

    @Operation(summary = "Get job status", 
               description = "Get the current status and progress of a processing job")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job status retrieved successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "jobId": 456,
                      "status": "COMPLETED",
                      "totalRows": 100,
                      "processedRows": 100,
                      "successCount": 95,
                      "failureCount": 5,
                      "errorReportPath": "uploads/123_errors_20251010_143045.csv",
                      "startedAt": "2025-10-10T14:30:00",
                      "completedAt": "2025-10-10T14:30:45"
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Job not found"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Status check failed: Database error"
                    }
                    """)))
    })
    @GetMapping("/jobs/status/{jobId}")
    public ResponseEntity<?> getJobStatus(
            @Parameter(description = "Job ID to get status for", required = true)
            @PathVariable Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            BulkDisputeJob job = jobOpt.get();
            return ResponseEntity.ok(Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus(),
                    "totalRows", job.getTotalRows(),
                    "processedRows", job.getProcessedRows(),
                    "successCount", job.getSuccessCount(),
                    "failureCount", job.getFailureCount(),
                    "errorReportPath", job.getErrorReportPath(),
                    "startedAt", job.getStartedAt(),
                    "completedAt", job.getCompletedAt()
            ));

        } catch (Exception e) {
            log.error("Error getting job status {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Status check failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get paginated list of jobs", 
               description = "Retrieve a paginated list of all jobs with optional filtering by status, session, and date range")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "jobs": [
                        {
                          "id": 456,
                          "sessionId": 123,
                          "jobRef": "JOB-1696923456789",
                          "status": "COMPLETED",
                          "totalRows": 100,
                          "processedRows": 100,
                          "successCount": 95,
                          "failureCount": 5,
                          "errorReportPath": "uploads/123_errors_20251010_143045.csv",
                          "startedAt": "2025-10-10T14:30:00",
                          "completedAt": "2025-10-10T14:30:45",
                          "createdAt": "2025-10-10T14:29:30"
                        },
                        {
                          "id": 457,
                          "sessionId": 124,
                          "jobRef": "JOB-1696923456790",
                          "status": "RUNNING",
                          "totalRows": 250,
                          "processedRows": 150,
                          "successCount": 140,
                          "failureCount": 10,
                          "errorReportPath": null,
                          "startedAt": "2025-10-10T15:00:00",
                          "completedAt": null,
                          "createdAt": "2025-10-10T14:59:30"
                        }
                      ],
                      "pagination": {
                        "currentPage": 0,
                        "pageSize": 20,
                        "totalPages": 3,
                        "totalElements": 45
                      }
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Failed to retrieve jobs: Database connection error"
                    }
                    """)))
    })
    @GetMapping("/jobs")
    public ResponseEntity<?> getJobs(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Number of items per page")
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "Filter by job status (PENDING, RUNNING, COMPLETED, FAILED)")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter by session ID")
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @Parameter(description = "Filter by date range start (ISO format: 2025-10-10T00:00:00)")
            @RequestParam(value = "startDate", required = false) String startDate,
            @Parameter(description = "Filter by date range end (ISO format: 2025-10-10T23:59:59)")
            @RequestParam(value = "endDate", required = false) String endDate,
            @Parameter(description = "Sort by field (id, status, createdAt, completedAt)")
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc, desc)")
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        try {
            // Parse date filters
            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;
            
            if (startDate != null && !startDate.trim().isEmpty()) {
                startDateTime = LocalDateTime.parse(startDate);
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                endDateTime = LocalDateTime.parse(endDate);
            }
            
            // Get jobs with filters
            List<BulkDisputeJob> jobs = jobRepository.findJobsWithFilters(
                    page, size, status, sessionId, startDateTime, endDateTime, sortBy, sortDir);
            
            // Get total count for pagination
            long totalElements = jobRepository.countJobsWithFilters(status, sessionId, startDateTime, endDateTime);
            int totalPages = (int) Math.ceil((double) totalElements / size);
            
            // Convert jobs to response format
            List<Map<String, Object>> jobResponses = jobs.stream()
                    .map(job -> {
                        Map<String, Object> jobMap = new java.util.HashMap<>();
                        jobMap.put("id", job.getId());
                        jobMap.put("sessionId", job.getSessionId());
                        jobMap.put("jobRef", job.getJobRef());
                        jobMap.put("status", job.getStatus());
                        jobMap.put("totalRows", job.getTotalRows());
                        jobMap.put("processedRows", job.getProcessedRows());
                        jobMap.put("successCount", job.getSuccessCount());
                        jobMap.put("failureCount", job.getFailureCount());
                        jobMap.put("errorReportPath", job.getErrorReportPath());
                        jobMap.put("startedAt", job.getStartedAt());
                        jobMap.put("completedAt", job.getCompletedAt());
                        jobMap.put("createdAt", job.getCreatedAt());
                        return jobMap;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                    "jobs", jobResponses,
                    "pagination", Map.of(
                            "currentPage", page,
                            "pageSize", size,
                            "totalPages", totalPages,
                            "totalElements", totalElements
                    )
            ));
            
        } catch (Exception e) {
            log.error("Error getting jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve jobs: " + e.getMessage()));
        }
    }

    @Operation(summary = "Download session file", 
               description = "Download the CSV file for a session as a blob")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File downloaded successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "filePath": "uploads/123.csv"
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Session not found"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Download failed: File not found"
                    }
                    """)))
    })
    @GetMapping("/sessions/file/{sessionId}")
    public ResponseEntity<?> downloadFile(@PathVariable Long sessionId) {
        String filePath = sessionService.getSessionFilePath(sessionId);
        
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of("filePath", filePath));
    }

    @Operation(summary = "Preview session file as blob", 
               description = "Get the CSV file content as a downloadable blob for preview")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File blob returned successfully",
                    content = @Content(mediaType = "text/csv", schema = @Schema(example = """
                    Unique Key,Action,Proof(Optional)
                    2214B8JO003524000000003524,ACCEPT,
                    2070EXNV012946000000012946,REJECT,Document1.pdf
                    207076AQ001506000000001506,ACCEPT,
                    """))),
        @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Session not found"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "File blob failed: File not found"
                    }
                    """)))
    })
    @GetMapping(value = "/sessions/preview/blob/{sessionId}", produces = "text/csv")
    public ResponseEntity<?> previewFileBlob(@PathVariable Long sessionId) {
        try {
            byte[] fileContent = sessionService.getSessionFileBlob(sessionId);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"disputes.csv\"")
                    .header("Content-Type", "text/csv")
                    .body(fileContent);

        } catch (Exception e) {
            log.error("Error getting file blob for session {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File blob failed: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get paginated list of sessions", 
               description = "Retrieve a paginated list of all sessions with optional filtering")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "sessions": [
                        {
                          "id": 123,
                          "uploadedBy": "user123",
                          "fileName": "disputes.csv",
                          "status": "CONFIRMED",
                          "totalRows": 100,
                          "validRows": 95,
                          "invalidRows": 5,
                          "createdAt": "2025-10-10T09:15:30",
                          "updatedAt": "2025-10-10T09:16:45"
                        },
                        {
                          "id": 124,
                          "uploadedBy": "admin",
                          "fileName": "bulk_disputes.csv",
                          "status": "PROCESSED",
                          "totalRows": 250,
                          "validRows": 250,
                          "invalidRows": 0,
                          "createdAt": "2025-10-10T08:30:15",
                          "updatedAt": "2025-10-10T08:35:22"
                        }
                      ],
                      "pagination": {
                        "currentPage": 0,
                        "pageSize": 20,
                        "totalPages": 5,
                        "totalElements": 100
                      }
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Failed to retrieve sessions: Database connection error"
                    }
                    """)))
    })
    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Number of items per page")
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "Filter by session status")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Filter by uploaded by user")
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            @Parameter(description = "Filter by institution code")
            @RequestParam(value = "institutionCode", required = false) String institutionCode,
            @Parameter(description = "Filter by merchant ID")
            @RequestParam(value = "merchantId", required = false) String merchantId) {
        try {
            BulkDisputeSessionService.SessionListResult result = sessionService.getSessions(page, size, status, uploadedBy, institutionCode, merchantId);
            
            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", result.getError()));
            }
            
            return ResponseEntity.ok(Map.of(
                    "sessions", result.getSessions(),
                    "pagination", Map.of(
                            "currentPage", result.getCurrentPage(),
                            "pageSize", result.getPageSize(),
                            "totalPages", result.getTotalPages(),
                            "totalElements", result.getTotalElements()
                    )
            ));
            
        } catch (Exception e) {
            log.error("Error getting sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve sessions: " + e.getMessage()));
        }
    }

    // ===============================
    // PROOF UPLOAD ENDPOINTS
    // ===============================

    @Operation(summary = "Upload proof file for dispute", 
               description = "Upload a proof file for a dispute. The filename should be the dispute's unique code (e.g., '2214B8JO003524000000003524.pdf')")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Proof uploaded successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "success": true,
                      "message": "Proof uploaded successfully",
                      "uniqueCode": "2214B8JO003524000000003524",
                      "filePath": "/path/to/proof/file.pdf"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Proof file is empty"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Failed to upload proof: File too large"
                    }
                    """)))
    })
    @PostMapping(value = "/proofs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProof(
            @Parameter(description = "Proof file to upload (filename should be the dispute unique code)", required = true)
            @RequestParam("file") MultipartFile file) {
        
        try {
            // Extract unique code from filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "File must have a valid filename (should be the dispute unique code)"));
            }
            
            // Remove file extension to get unique code
            String uniqueCode = extractUniqueCodeFromFilename(originalFilename);
            if (uniqueCode == null || uniqueCode.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid filename format. Filename should be the dispute unique code (e.g., '2214B8JO003524000000003524.pdf')"));
            }
            
            String filePath = proofService.uploadProof(uniqueCode, file);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Proof uploaded successfully",
                    "uniqueCode", uniqueCode,
                    "filePath", filePath
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid proof upload request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading proof", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload proof: " + e.getMessage()));
        }
    }
    
    private String extractUniqueCodeFromFilename(String filename) {
        // Remove file extension
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return filename; // No extension, assume entire filename is unique code
        }
        return filename.substring(0, lastDotIndex);
    }

    @Operation(summary = "Download proof file for dispute", 
               description = "Download the proof file for a specific dispute")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Proof file downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "Proof file not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Proof file not found"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Failed to download proof"
                    }
                    """)))
    })
    @GetMapping("/proofs/download/{uniqueCode}")
    public ResponseEntity<?> downloadProof(
            @Parameter(description = "Dispute unique code", required = true)
            @PathVariable String uniqueCode) {
        
        try {
            byte[] fileContent = proofService.downloadProof(uniqueCode);
            String filePath = proofService.getProofFilePath(uniqueCode);
            
            // Extract filename from path
            String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(fileContent);
            
        } catch (IOException e) {
            log.warn("Proof file not found for uniqueCode: {}", uniqueCode);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Proof file not found"));
        } catch (Exception e) {
            log.error("Error downloading proof for uniqueCode: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to download proof"));
        }
    }

    @Operation(summary = "Check if proof exists for dispute", 
               description = "Check if a proof file exists for a specific dispute")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Proof status retrieved successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "uniqueCode": "2214B8JO003524000000003524",
                      "proofExists": true,
                      "filePath": "/path/to/proof/file.pdf"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Failed to check proof status"
                    }
                    """)))
    })
    @GetMapping("/proofs/status/{uniqueCode}")
    public ResponseEntity<?> checkProofStatus(
            @Parameter(description = "Dispute unique code", required = true)
            @PathVariable String uniqueCode) {
        
        try {
            boolean exists = proofService.proofExists(uniqueCode);
            String filePath = exists ? proofService.getProofFilePath(uniqueCode) : null;
            
            return ResponseEntity.ok(Map.of(
                    "uniqueCode", uniqueCode,
                    "proofExists", exists,
                    "filePath", filePath != null ? filePath : ""
            ));
            
        } catch (Exception e) {
            log.error("Error checking proof status for uniqueCode: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check proof status"));
        }
    }

    @Operation(summary = "Delete proof file for dispute", 
               description = "Delete the proof file for a specific dispute")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Proof deleted successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "success": true,
                      "message": "Proof deleted successfully"
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Proof file not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Proof file not found"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Failed to delete proof"
                    }
                    """)))
    })
    @DeleteMapping("/proofs/delete/{uniqueCode}")
    public ResponseEntity<?> deleteProof(
            @Parameter(description = "Dispute unique code", required = true)
            @PathVariable String uniqueCode) {
        
        try {
            boolean deleted = proofService.deleteProof(uniqueCode);
            
            if (deleted) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Proof deleted successfully"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Proof file not found"));
            }
            
        } catch (Exception e) {
            log.error("Error deleting proof for uniqueCode: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete proof"));
        }
    }

    // ===============================
    // RECOVERY APIs
    // ===============================

    @Operation(summary = "Resume a paused job", 
               description = "Resume a job that was paused due to infrastructure issues")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job resumed successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "success": true,
                      "message": "Job resumed successfully"
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Job not found"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Job cannot be resumed",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Job is not paused"
                    }
                    """)))
    })
    @PostMapping("/jobs/{jobId}/resume")
    public ResponseEntity<?> resumeJob(@PathVariable Long jobId) {
        try {
            boolean success = jobResumeService.resumeJob(jobId);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Job resumed successfully",
                    "resumePoint", jobResumeService.getResumePoint(jobId)
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Job cannot be resumed"));
            }
            
        } catch (Exception e) {
            log.error("Error resuming job: {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to resume job"));
        }
    }

    @Operation(summary = "Pause a running job", 
               description = "Pause a job due to infrastructure issues")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job paused successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "success": true,
                      "message": "Job paused successfully"
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Job not found"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Job cannot be paused",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Job is not running"
                    }
                    """)))
    })
    @PostMapping("/jobs/{jobId}/pause")
    public ResponseEntity<?> pauseJob(
            @PathVariable Long jobId,
            @RequestParam(required = false, defaultValue = "Manual pause") String reason) {
        try {
            boolean success = jobResumeService.pauseJob(jobId, reason);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Job paused successfully",
                    "reason", reason
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Job cannot be paused"));
            }
            
        } catch (Exception e) {
            log.error("Error pausing job: {}", jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to pause job"));
        }
    }

    @Operation(summary = "Retry a failed row", 
               description = "Retry processing a specific failed row for transient failures")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Row retry initiated successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "success": true,
                      "message": "Row retry initiated successfully"
                    }
                    """))),
        @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Job not found"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Row cannot be retried",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Row cannot be retried"
                    }
                    """)))
    })
    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<?> retryFailedRow(
            @PathVariable Long jobId,
            @RequestParam int rowNumber,
            @RequestParam String rowData,
            @RequestParam(required = false, defaultValue = "3") int maxRetries) {
        try {
            boolean success = jobRetryService.retryFailedRow(jobId, rowNumber, rowData, maxRetries);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Row retry initiated successfully",
                    "rowNumber", rowNumber,
                    "maxRetries", maxRetries
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Row cannot be retried"));
            }
            
        } catch (Exception e) {
            log.error("Error retrying row {} for job {}: {}", rowNumber, jobId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retry row"));
        }
    }

    // ===============================
    // COMBINED VALIDATION ENDPOINT
    // ===============================

    @Operation(summary = "Validate session and proof files", 
               description = "Validate a CSV file along with corresponding proof files in a single request. " +
                           "This endpoint performs all validations from the session and proof endpoints combined, " +
                           "and saves the files to appropriate folders (CSV to uploads, proofs to proofs folder).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation completed successfully",
                    content = @Content(schema = @Schema(example = """
                    {
                      "totals": {
                        "requested": 5,
                        "malformedRows": 0,
                        "succeeded": 5,
                        "failed": 0,
                        "elapsedMs": 353
                      },
                      "accepted": {
                        "slated": 2,
                        "succeeded": 2,
                        "failed": 0
                      },
                      "rejected": {
                        "slated": 3,
                        "succeeded": 3,
                        "failed": 0,
                        "missingReceipt": 0
                      },
                      "sessionId": 123
                    }
                    """))),
        @ApiResponse(responseCode = "422", description = "Validation failed",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Validation failed",
                      "validationErrors": [
                        {
                          "row": 0,
                          "column": "HEADER",
                          "reason": "Missing required column: Unique Key"
                        },
                        {
                          "row": 5,
                          "column": "Action",
                          "reason": "Invalid action value 'PENDING'. Must be one of: [ACCEPT, REJECT]"
                        },
                        {
                          "row": 3,
                          "column": "Proof",
                          "reason": "Proof file is mandatory for REJECT actions. Please provide proof file for: 2214B8JO003524000000003524"
                        }
                      ]
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "CSV file is required"
                    }
                    """))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(example = """
                    {
                      "error": "Validation failed: File processing error"
                    }
                    """)))
    })
    @PostMapping(value = "/sessions/session-and-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> validateSessionAndProof(
            @Parameter(description = "CSV file containing dispute data", required = true)
            @RequestParam("csvFile") MultipartFile csvFile,
            @Parameter(description = "Proof files for REJECT actions (filename should be the dispute unique code)", required = false)
            @RequestParam(value = "receiptFiles", required = false) List<MultipartFile> receiptFiles,
            @Parameter(description = "User who uploaded the files")
            @RequestParam(value = "uploadedBy", defaultValue = "system") String uploadedBy,
            @Parameter(description = "Institution code")
            @RequestParam("institutionCode") String institutionCode,
            @Parameter(description = "Merchant ID")
            @RequestParam("merchantId") String merchantId) {
        
        try {
            log.info("Received request for session and proof validation. CSV: {}, Proof files: {}", 
                    csvFile != null ? csvFile.getOriginalFilename() : "null",
                    receiptFiles != null ? receiptFiles.size() : 0);
            
            Map<String, Object> result = combinedValidationService.validateSessionAndProofs(
                    csvFile, receiptFiles, uploadedBy, institutionCode, merchantId);
            
            if (result == null) {
                log.error("Service returned null result");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Service returned null result"));
            }
            
            if (result.containsKey("error")) {
                log.warn("Validation failed: {}", result.get("error"));
                if (result.containsKey("validationErrors")) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
                }
            }
            
            log.info("Validation successful, returning response");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Unexpected error in controller while validating session and proof files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

}
