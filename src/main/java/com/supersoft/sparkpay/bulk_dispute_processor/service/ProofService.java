package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProofService {
    
    /**
     * Upload a proof file for a specific dispute
     * @param uniqueCode The dispute's unique code (will be used as filename)
     * @param file The proof file to upload
     * @return The file path where the proof was saved
     * @throws IOException if file operations fail
     */
    String uploadProof(String uniqueCode, MultipartFile file) throws IOException;
    
    /**
     * Upload proof file without validation (for internal use by joint validation service)
     * @param uniqueCode Unique code for the dispute
     * @param file Proof file to upload
     * @return File path where the proof was saved
     * @throws IOException if file operations fail
     */
    String uploadProofWithoutValidation(String uniqueCode, MultipartFile file) throws IOException;
    
    /**
     * Upload proof file without validation with replace existing control
     * @param uniqueCode Unique code for the dispute
     * @param file Proof file to upload
     * @param replaceExisting Whether to replace existing files
     * @return File path where the proof was saved
     * @throws IOException if file operations fail
     */
    String uploadProofWithoutValidation(String uniqueCode, MultipartFile file, boolean replaceExisting) throws IOException;
    
    /**
     * Get the proof file path for a dispute
     * @param uniqueCode The dispute's unique code
     * @return The file path if exists, null otherwise
     */
    String getProofFilePath(String uniqueCode);
    
    /**
     * Check if a proof file exists for a dispute
     * @param uniqueCode The dispute's unique code
     * @return true if proof exists, false otherwise
     */
    boolean proofExists(String uniqueCode);
    
    /**
     * Delete a proof file for a dispute
     * @param uniqueCode The dispute's unique code
     * @return true if deleted successfully, false otherwise
     */
    boolean deleteProof(String uniqueCode);
    
    /**
     * Download a proof file
     * @param uniqueCode The dispute's unique code
     * @return The file content as bytes
     * @throws IOException if file operations fail
     */
    byte[] downloadProof(String uniqueCode) throws IOException;
}
