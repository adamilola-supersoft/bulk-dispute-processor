package com.supersoft.sparkpay.bulk_dispute_processor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FailureClassifier {

    // Patterns for transient failures
    private static final Pattern TRANSIENT_PATTERNS = Pattern.compile(
        "(?i).*(timeout|connection|lock|deadlock|temporary|network|unavailable).*"
    );

    // Patterns for infrastructure failures
    private static final Pattern INFRASTRUCTURE_PATTERNS = Pattern.compile(
        "(?i).*(database|file not found|permission denied|disk full|memory).*"
    );

    /**
     * Classify failure type based on exception and message
     */
    public FailureType classifyFailure(Exception exception, String message) {
        String errorMessage = message != null ? message : exception.getMessage();
        
        if (errorMessage == null) {
            return FailureType.PERMANENT; // Unknown error, treat as permanent
        }

        // Check for transient patterns
        if (TRANSIENT_PATTERNS.matcher(errorMessage).matches()) {
            return FailureType.TRANSIENT;
        }

        // Check for infrastructure patterns
        if (INFRASTRUCTURE_PATTERNS.matcher(errorMessage).matches()) {
            return FailureType.INFRASTRUCTURE;
        }

        // Check exception type
        if (exception instanceof SQLException) {
            SQLException sqlEx = (SQLException) exception;
            int errorCode = sqlEx.getErrorCode();
            
            // Common transient SQL error codes
            if (errorCode == 1205 || errorCode == 1222 || errorCode == 1213) { // Lock timeout, deadlock
                return FailureType.TRANSIENT;
            }
            if (errorCode == 2003 || errorCode == 2006) { // Connection lost
                return FailureType.INFRASTRUCTURE;
            }
        }

        // Check for specific exception types
        if (exception instanceof java.net.ConnectException ||
            exception instanceof java.net.SocketTimeoutException ||
            exception instanceof java.util.concurrent.TimeoutException) {
            return FailureType.TRANSIENT;
        }

        if (exception instanceof java.io.FileNotFoundException ||
            exception instanceof java.nio.file.AccessDeniedException) {
            return FailureType.INFRASTRUCTURE;
        }

        // Default to permanent for business logic errors
        return FailureType.PERMANENT;
    }

    /**
     * Determine if a failure should be retried
     */
    public boolean shouldRetry(FailureType failureType, int retryCount, int maxRetries) {
        switch (failureType) {
            case TRANSIENT:
                return retryCount < maxRetries;
            case INFRASTRUCTURE:
                return retryCount < 3; // Limited retries for infrastructure issues
            case PERMANENT:
                return false; // Never retry permanent failures
            default:
                return false;
        }
    }

    /**
     * Get retry delay based on failure type and retry count
     */
    public long getRetryDelay(FailureType failureType, int retryCount) {
        switch (failureType) {
            case TRANSIENT:
                return Math.min(1000 * (1L << retryCount), 30000); // Exponential backoff, max 30s
            case INFRASTRUCTURE:
                return 5000 * (retryCount + 1); // Linear backoff, 5s, 10s, 15s
            default:
                return 1000; // 1 second default
        }
    }

    public enum FailureType {
        TRANSIENT,      // Temporary issues that might succeed on retry
        PERMANENT,      // Business logic errors that won't succeed on retry
        INFRASTRUCTURE  // System issues that need attention
    }
}
