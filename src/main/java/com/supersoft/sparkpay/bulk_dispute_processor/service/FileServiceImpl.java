package com.supersoft.sparkpay.bulk_dispute_processor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Value("${bulk.files.base-path:C:/Users/USER/Downloads/sparkpay.bulk_dispute_processor/sparkpay.bulk_dispute_processor/uploads}")
    private String basePath;

    public String saveSessionFile(Long sessionId, MultipartFile file) throws IOException {
        Path baseDir = Paths.get(basePath);
        Files.createDirectories(baseDir);
        
        String fileName = sessionId + ".csv";
        Path filePath = baseDir.resolve(fileName);
        
        // Write to temp file first, then atomically move to final location
        Path tempPath = baseDir.resolve(fileName + ".tmp");
        
        try {
            Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupException) {
                log.warn("Failed to clean up temp file: {}", tempPath, cleanupException);
            }
            throw e;
        }
        
        log.info("Saved session file: {}", filePath);
        return filePath.toString();
    }

    public String overwriteSessionFile(Long sessionId, MultipartFile file) throws IOException {
        Path baseDir = Paths.get(basePath);
        String fileName = sessionId + ".csv";
        Path filePath = baseDir.resolve(fileName);
        
        // Backup the existing file before overwriting
        if (Files.exists(filePath)) {
            String backupFileName = sessionId + "_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
            Path backupPath = baseDir.resolve(backupFileName);
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Created backup: {}", backupPath);
        }
        Path tempPath = baseDir.resolve(fileName + ".tmp");
        
        try {
            Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupException) {
                log.warn("Failed to clean up temp file: {}", tempPath, cleanupException);
            }
            throw e;
        }
        
        log.info("Overwritten session file: {}", filePath);
        return filePath.toString();
    }

    public Path getSessionFilePath(Long sessionId) {
        Path baseDir = Paths.get(basePath);
        String fileName = sessionId + ".csv";
        return baseDir.resolve(fileName);
    }

    public boolean sessionFileExists(Long sessionId) {
        Path filePath = getSessionFilePath(sessionId);
        return Files.exists(filePath);
    }

    public void deleteSessionFile(Long sessionId) {
        Path filePath = getSessionFilePath(sessionId);
        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted session file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete session file: {}", filePath, e);
        }
    }

    public String createErrorReportPath(Long sessionId) {
        Path baseDir = Paths.get(basePath);
        String fileName = sessionId + "_errors_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        return baseDir.resolve(fileName).toString();
    }
}
