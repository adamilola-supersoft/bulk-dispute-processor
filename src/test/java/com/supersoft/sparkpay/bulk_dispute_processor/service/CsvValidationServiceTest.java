package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class CsvValidationServiceTest {

    private CsvValidationServiceImpl validationService;

    @BeforeEach
    void setUp() {
        validationService = new CsvValidationServiceImpl();
    }

    @Test
    void testValidCsv() {
        String csvContent = "Unique Key,Action,Proof(Optional)\n" +
                "9070NMN,Reject,7.96E+13\n" +
                "9070KS1W,Accept,";
        
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", csvContent.getBytes());
        
        CsvValidationService.ValidationResult result = validationService.validateCsv(file);
        
        assertTrue(result.isValid());
        assertEquals(2, result.getTotalRows());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    void testMissingRequiredColumn() {
        String csvContent = "Unique Key,Proof(Optional)\n" +
                "9070NMN,7.96E+13";
        
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", csvContent.getBytes());
        
        CsvValidationService.ValidationResult result = validationService.validateCsv(file);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Missing required column: Action"));
    }

    @Test
    void testInvalidAction() {
        String csvContent = "Unique Key,Action,Proof(Optional)\n" +
                "9070NMN,INVALID,7.96E+13";
        
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", csvContent.getBytes());
        
        CsvValidationService.ValidationResult result = validationService.validateCsv(file);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorSummary().contains("Invalid action value"));
    }

    @Test
    void testEmptyFile() {
        String csvContent = "";
        
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", csvContent.getBytes());
        
        CsvValidationService.ValidationResult result = validationService.validateCsv(file);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("File is empty"));
    }

    @Test
    void testInvalidUniqueKe() {
        String csvContent = "Unique Key,Action,Proof(Optional)\n" +
                "invalid-id,Accept,7.96E+13";
        
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", csvContent.getBytes());
        
        CsvValidationService.ValidationResult result = validationService.validateCsv(file);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorSummary().contains("Invalid Unique Key format"));
    }
}
