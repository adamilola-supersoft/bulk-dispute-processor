package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisputeUpdaterImplTest {

    @Mock
    private DisputeRepository disputeRepository;

    private DisputeUpdaterImpl disputeUpdater;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        disputeUpdater = new DisputeUpdaterImpl();
        // We need to inject the mock repository manually since we're not using Spring context
        disputeUpdater.disputeRepository = disputeRepository;
    }

    @Test
    void testProcessRowSuccess() {
        Map<String, String> row = new HashMap<>();
        row.put("Unique Key", "9070NMN");
        row.put("Action", "ACCEPT");
        row.put("Proof(Optional)", "document.pdf");
        row.put("uploadedBy", "testuser");
        
        when(disputeRepository.updateDisputeStatus(anyString(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(1);
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(row);
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        verify(disputeRepository).updateDisputeStatus("9070NMN", "testuser", 0, 0, "document.pdf");
    }

    @Test
    void testProcessRowReject() {
        Map<String, String> row = new HashMap<>();
        row.put("Unique Key", "9070KS1W");
        row.put("Action", "REJECT");
        row.put("Proof(Optional)", "rejection_doc.pdf");
        row.put("uploadedBy", "admin");
        
        when(disputeRepository.updateDisputeStatus(anyString(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(1);
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(row);
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        verify(disputeRepository).updateDisputeStatus("9070KS1W", "admin", 1, 1, "rejection_doc.pdf");
    }

    @Test
    void testProcessRowMissingUniqueKey() {
        Map<String, String> row = new HashMap<>();
        row.put("Action", "ACCEPT");
        row.put("uploadedBy", "testuser");
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(row);
        
        assertFalse(result.isSuccess());
        assertEquals("Unique Key is required", result.getErrorMessage());
        verifyNoInteractions(disputeRepository);
    }

    @Test
    void testProcessRowMissingAction() {
        Map<String, String> row = new HashMap<>();
        row.put("Unique Key", "9070NMN");
        row.put("uploadedBy", "testuser");
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(row);
        
        assertFalse(result.isSuccess());
        assertEquals("Action is required", result.getErrorMessage());
        verifyNoInteractions(disputeRepository);
    }

    @Test
    void testProcessRowNoMatchingDispute() {
        Map<String, String> row = new HashMap<>();
        row.put("Unique Key", "INVALID123");
        row.put("Action", "ACCEPT");
        row.put("uploadedBy", "testuser");
        
        when(disputeRepository.updateDisputeStatus(anyString(), anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(0);
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(row);
        
        assertFalse(result.isSuccess());
        assertEquals("No matching dispute found or already processed", result.getErrorMessage());
    }

    @Test
    void testProcessRowDatabaseError() {
        Map<String, String> row = new HashMap<>();
        row.put("Unique Key", "9070NMN");
        row.put("Action", "ACCEPT");
        row.put("uploadedBy", "testuser");
        
        when(disputeRepository.updateDisputeStatus(anyString(), anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(row);
        
        assertFalse(result.isSuccess());
        assertEquals("Database error: Database connection failed", result.getErrorMessage());
    }

    @Test
    void testMapActionToStatus() {
        // Test ACCEPT mapping
        Map<String, String> acceptRow = new HashMap<>();
        acceptRow.put("Unique Key", "9070NMN");
        acceptRow.put("Action", "ACCEPT");
        acceptRow.put("uploadedBy", "testuser");
        
        when(disputeRepository.updateDisputeStatus(anyString(), anyString(), eq(0), eq(0), anyString()))
                .thenReturn(1);
        
        DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(acceptRow);
        assertTrue(result.isSuccess());
        
        // Reset mock for second test
        reset(disputeRepository);
        
        // Test REJECT mapping
        Map<String, String> rejectRow = new HashMap<>();
        rejectRow.put("Unique Key", "9070KS1W");
        rejectRow.put("Action", "REJECT");
        rejectRow.put("uploadedBy", "testuser");
        
        when(disputeRepository.updateDisputeStatus(anyString(), anyString(), eq(1), eq(1), anyString()))
                .thenReturn(1);
        
        result = disputeUpdater.processRow(rejectRow);
        assertTrue(result.isSuccess());
    }
}
