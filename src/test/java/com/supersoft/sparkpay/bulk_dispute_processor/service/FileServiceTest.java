package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private FileServiceImpl fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl();
        ReflectionTestUtils.setField(fileService, "basePath", tempDir.toString());
    }

    @Test
    void testSaveSessionFile() throws IOException {
        String content = "dispute_id,action,reason,notes\n123,ACCEPT,Resolved,Good customer";
        MockMultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", content.getBytes());
        
        String filePath = fileService.saveSessionFile(1L, file);
        
        assertNotNull(filePath);
        assertTrue(Files.exists(Path.of(filePath)));
        assertEquals(content, Files.readString(Path.of(filePath)));
    }

    @Test
    void testOverwriteSessionFile() throws IOException {
        String originalContent = "dispute_id,action,reason,notes\n123,ACCEPT,Resolved,Good customer";
        String newContent = "dispute_id,action,reason,notes\n123,REJECT,Fraud,Multiple chargebacks";
        
        MockMultipartFile originalFile = new MockMultipartFile("test.csv", "test.csv", "text/csv", originalContent.getBytes());
        MockMultipartFile newFile = new MockMultipartFile("test.csv", "test.csv", "text/csv", newContent.getBytes());
        
        // Save original file
        fileService.saveSessionFile(1L, originalFile);
        
        // Overwrite with new content
        String filePath = fileService.overwriteSessionFile(1L, newFile);
        
        assertNotNull(filePath);
        assertTrue(Files.exists(Path.of(filePath)));
        assertEquals(newContent, Files.readString(Path.of(filePath)));
    }

    @Test
    void testGetSessionFilePath() {
        Path filePath = fileService.getSessionFilePath(1L);
        
        assertTrue(filePath.toString().endsWith("1.csv"));
    }

    @Test
    void testSessionFileExists() throws IOException {
        String content = "dispute_id,action,reason,notes\n123,ACCEPT,Resolved,Good customer";
        MockMultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", content.getBytes());
        
        assertFalse(fileService.sessionFileExists(1L));
        
        fileService.saveSessionFile(1L, file);
        
        assertTrue(fileService.sessionFileExists(1L));
    }

    @Test
    void testDeleteSessionFile() throws IOException {
        String content = "dispute_id,action,reason,notes\n123,ACCEPT,Resolved,Good customer";
        MockMultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", content.getBytes());
        
        fileService.saveSessionFile(1L, file);
        assertTrue(fileService.sessionFileExists(1L));
        
        fileService.deleteSessionFile(1L);
        assertFalse(fileService.sessionFileExists(1L));
    }
}
