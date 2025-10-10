package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface FileService {
    String saveSessionFile(Long sessionId, MultipartFile file) throws IOException;
    String overwriteSessionFile(Long sessionId, MultipartFile file) throws IOException;
    Path getSessionFilePath(Long sessionId);
    boolean sessionFileExists(Long sessionId);
    void deleteSessionFile(Long sessionId);
    String createErrorReportPath(Long sessionId);
}
