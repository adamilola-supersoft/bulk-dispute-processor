package com.supersoft.sparkpay.bulk_dispute_processor.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CsvParser {
    
    /**
     * Parse a CSV line into a list of fields, handling quotes and commas correctly
     * @param line The CSV line to parse
     * @return List of parsed fields
     */
    public static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        // Remove BOM if present
        if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(cleanField(currentField.toString()));
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        result.add(cleanField(currentField.toString()));
        return result;
    }
    
    /**
     * Clean a CSV field by removing various types of whitespace and invisible characters
     * @param field The raw field value
     * @return Cleaned field value
     */
    private static String cleanField(String field) {
        if (field == null) {
            return "";
        }
        
        // Remove all types of whitespace and invisible characters
        return field.replaceAll("\\s+", " ")  // Replace multiple whitespace with single space
                   .replaceAll("^\\s+|\\s+$", "")  // Remove leading/trailing whitespace
                   .replaceAll("[\\u00A0\\u2000-\\u200F\\u2028-\\u202F\\u205F-\\u206F\\u3000]", "")  // Remove various Unicode spaces
                   .replaceAll("[\\uFEFF]", "")  // Remove BOM if present in field
                   .trim();
    }
    
    /**
     * Check if an InputStream starts with BOM and return a clean InputStream
     * @param inputStream The original input stream
     * @return InputStream without BOM
     * @throws IOException if reading fails
     */
    public static InputStream removeBomFromStream(InputStream inputStream) throws IOException {
        // Read the first 3 bytes to check for BOM
        byte[] bom = new byte[3];
        int bytesRead = inputStream.read(bom);
        
        if (bytesRead == 3 && 
            bom[0] == (byte) 0xEF && 
            bom[1] == (byte) 0xBB && 
            bom[2] == (byte) 0xBF) {
            // BOM detected, return stream without BOM (stream is already positioned after BOM)
            return inputStream;
        } else {
            // No BOM, create a new stream that includes the bytes we read
            byte[] allBytes = new byte[bytesRead + 1024]; // Start with some buffer
            System.arraycopy(bom, 0, allBytes, 0, bytesRead);
            
            // Read the rest of the stream
            int totalBytes = bytesRead;
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (totalBytes + read > allBytes.length) {
                    // Resize array if needed
                    byte[] newArray = new byte[allBytes.length * 2];
                    System.arraycopy(allBytes, 0, newArray, 0, totalBytes);
                    allBytes = newArray;
                }
                System.arraycopy(buffer, 0, allBytes, totalBytes, read);
                totalBytes += read;
            }
            
            // Create a new stream from the combined bytes
            return new java.io.ByteArrayInputStream(allBytes, 0, totalBytes);
        }
    }
    
    /**
     * Check if a string starts with BOM
     * @param text The text to check
     * @return true if text starts with BOM
     */
    public static boolean hasBom(String text) {
        return text.startsWith("\uFEFF");
    }
    
    /**
     * Debug method to show invisible characters in a string
     * @param text The text to analyze
     * @return String with invisible characters made visible
     */
    public static String showInvisibleChars(String text) {
        if (text == null) return "null";
        
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c) || c == '\uFEFF') {
                result.append("[").append((int)c).append("]");
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
