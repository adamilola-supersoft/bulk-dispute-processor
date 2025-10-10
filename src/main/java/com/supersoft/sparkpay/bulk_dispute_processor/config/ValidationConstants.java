package com.supersoft.sparkpay.bulk_dispute_processor.config;

import java.util.Arrays;
import java.util.List;

public class ValidationConstants {
    
    // Required columns that must be present in CSV
    public static final List<String> REQUIRED_COLUMNS = Arrays.asList("Unique Key", "Action");
    
    // Expected columns (all columns that should be present)
    public static final List<String> EXPECTED_COLUMNS = Arrays.asList("Unique Key", "Action", "Proof(Optional)");
    
    // Valid action values (case-insensitive)
    public static final List<String> VALID_ACTIONS = Arrays.asList("ACCEPT", "REJECT", "Accept", "Reject");
    
    // Maximum file size in MB
    public static final int MAX_UPLOAD_SIZE_MB = 50;
    
    // Maximum preview rows
    public static final int MAX_PREVIEW_ROWS = 200;
    
    // Session TTL in days
    public static final int SESSION_TTL_DAYS = 7;
}
