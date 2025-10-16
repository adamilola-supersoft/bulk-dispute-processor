package com.supersoft.sparkpay.bulk_dispute_processor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class ProofServiceImpl implements ProofService {

    @Value("${bulk.proofs.base-path:C:/Users/USER/Downloads/sparkpay.bulk_dispute_processor/sparkpay.bulk_dispute_processor/proofs}")
    private String basePath;

    @Value("${bulk.proofs.max-size-mb:10}")
    private int maxSizeMb;

    @Value("${bulk.proofs.allowed-extensions:pdf,jpg,jpeg,png,doc,docx}")
    private String allowedExtensions;

    @Value("${bulk.proofs.replace-existing:true}")
    private boolean replaceExisting;

    @Override
    public String uploadProof(String uniqueCode, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Proof file is empty");
        }

        // Validate file size
        if (file.getSize() > (long) maxSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("Proof file too large. Maximum size: " + maxSizeMb + "MB");
        }

        // Validate file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Proof file must have a valid filename");
        }

        String fileExtension = getFileExtension(originalFilename);
        List<String> allowedExts = Arrays.asList(allowedExtensions.split(","));
        if (!allowedExts.contains(fileExtension.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Allowed extensions: " + allowedExtensions);
        }

        // Create base directory
        Path baseDir = Paths.get(basePath);
        Files.createDirectories(baseDir);

        // Check if proof already exists and handle based on configuration
        String existingFilePath = getProofFilePath(uniqueCode);
        if (existingFilePath != null) {
            if (replaceExisting) {
                try {
                    Files.deleteIfExists(Paths.get(existingFilePath));
                    log.info("Deleted existing proof file: {}", existingFilePath);
                } catch (IOException e) {
                    log.warn("Failed to delete existing proof file: {}", existingFilePath, e);
                }
            } else {
                throw new IllegalArgumentException("Proof already exists for unique code: " + uniqueCode + ". Set bulk.proofs.replace-existing=true to allow replacement.");
            }
        }

        // Generate filename using unique code and timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = uniqueCode + "_" + timestamp + "." + fileExtension;
        Path filePath = baseDir.resolve(filename);

        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Proof uploaded successfully: uniqueCode={}, filename={}, size={} bytes", 
                uniqueCode, filename, file.getSize());

        return filePath.toString();
    }

    @Override
    public String uploadProofWithoutValidation(String uniqueCode, MultipartFile file) throws IOException {
        // Only check for empty file (basic safety check)
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Proof file is empty");
        }

        // Create base directory
        Path baseDir = Paths.get(basePath);
        Files.createDirectories(baseDir);

        // Check if proof already exists and handle based on configuration
        String existingFilePath = getProofFilePath(uniqueCode);
        if (existingFilePath != null) {
            if (replaceExisting) {
                try {
                    Files.deleteIfExists(Paths.get(existingFilePath));
                    log.info("Deleted existing proof file: {}", existingFilePath);
                } catch (IOException e) {
                    log.warn("Failed to delete existing proof file: {}", existingFilePath, e);
                }
            } else {
                throw new IllegalArgumentException("Proof already exists for unique code: " + uniqueCode + ". Set bulk.proofs.replace-existing=true to allow replacement.");
            }
        }

        // Generate filename using unique code and timestamp
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = uniqueCode + "_" + timestamp + "." + fileExtension;
        Path filePath = baseDir.resolve(filename);

        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Proof uploaded successfully: uniqueCode={}, filename={}, size={} bytes", 
                uniqueCode, filename, file.getSize());

        return filePath.toString();
    }

    @Override
    public String getProofFilePath(String uniqueCode) {
        Path baseDir = Paths.get(basePath);
        if (!Files.exists(baseDir)) {
            return null;
        }

        try {
            return Files.list(baseDir)
                    .filter(path -> path.getFileName().toString().startsWith(uniqueCode + "_"))
                    .findFirst()
                    .map(Path::toString)
                    .orElse(null);
        } catch (IOException e) {
            log.error("Error finding proof file for uniqueCode: {}", uniqueCode, e);
            return null;
        }
    }

    @Override
    public boolean proofExists(String uniqueCode) {
        return getProofFilePath(uniqueCode) != null;
    }

    @Override
    public boolean deleteProof(String uniqueCode) {
        String filePath = getProofFilePath(uniqueCode);
        if (filePath == null) {
            return false;
        }

        try {
            Files.deleteIfExists(Paths.get(filePath));
            log.info("Proof deleted successfully: uniqueCode={}", uniqueCode);
            return true;
        } catch (IOException e) {
            log.error("Error deleting proof file for uniqueCode: {}", uniqueCode, e);
            return false;
        }
    }

    @Override
    public byte[] downloadProof(String uniqueCode) throws IOException {
        String filePath = getProofFilePath(uniqueCode);
        if (filePath == null) {
            throw new IOException("Proof file not found for uniqueCode: " + uniqueCode);
        }

        return Files.readAllBytes(Paths.get(filePath));
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("Invalid filename: " + filename);
        }
        return filename.substring(lastDotIndex + 1);
    }
}
